// vip-daemon：厂商 vip-daemon 的 arm64 等价实现（只读优先）。
//
// 为什么需要它：厂商只提供 amd64 的 systemd 二进制（amd64/vip/vip-daemon，-s -w 剥离符号），
// 而测试/交付集群是 arm64，跑不了。gaop-be 的 /v1/pub/vip 依赖它（feign GET /api/vips），
// 连不上就整条链路 500。接口契约见 recovered 源码：
//   service/vip/VipDaemonApi.java（7 个方法）、VipItem、InterfaceInfo。
//
// 设计取舍（单节点集群）：
//   · GET /api/vips       —— 返回本 daemon 管理的 VIP 列表，单节点无 HA 就是 []，
//                            这与厂商 config.json.j2 里 `node_list|length == 1 → vips: []` 一致，
//                            是真实状态，不是假数据。
//   · GET /api/interfaces —— 用标准库读真实网卡（不 exec ip 命令），过滤 ignore 前缀。
//   · 写接口              —— 明确返回 501 + 原因，绝不假装成功（避免"点了没反应"的假入口）。
//
// 构建：CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o vip-daemon .
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

// ---------- 契约类型（字段名必须与厂商 JSON 一致） ----------

// VipItem 对应 VipItem.java：{"interface": "...", "vip": "10.0.0.5/32"}
type VipItem struct {
	Interface string `json:"interface"`
	Vip       string `json:"vip"`
}

// InterfaceInfo 对应 InterfaceInfo.java。
// 注意 index 在 Java 侧是 @JsonIgnore，这里**故意不输出**，保持响应形状一致。
type InterfaceInfo struct {
	Name      string   `json:"name"`
	Mac       string   `json:"mac"`
	Flags     string   `json:"flags"`
	Addresses []string `json:"addresses"`
}

// Config 对齐 config.json.j2 的字段名，缺项用默认值，保证厂商模板能直接喂进来。
type Config struct {
	NodeID         string    `json:"node_id"`
	PrimaryNodeID  string    `json:"primary_node_id"`
	Interface      string    `json:"interface"`
	Vips           []VipItem `json:"vips"`
	Peers          []string  `json:"peers"`
	PeerPort       int       `json:"peer_port"`
	HTTPPort       int       `json:"http_port"`
	IgnorePrefixes []string  `json:"ignore_prefix_list"`
	ProtectedIPs   []string  `json:"protected_ip_list"`
	LogPath        string    `json:"log_path"`
}

func defaultConfig() Config {
	return Config{
		HTTPPort: 5556,
		PeerPort: 5555,
		IgnorePrefixes: []string{
			"lo", "docker", "kube-", "flannel", "usb", "virbr", "cni", "veth",
		},
		Vips: []VipItem{},
	}
}

func loadConfig(path string) Config {
	cfg := defaultConfig()
	if path == "" {
		return cfg
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		log.Printf("读取配置 %s 失败（用默认值继续）: %v", path, err)
		return cfg
	}
	if err := json.Unmarshal(raw, &cfg); err != nil {
		log.Printf("解析配置 %s 失败（用默认值继续）: %v", path, err)
		return cfg
	}
	if cfg.HTTPPort == 0 {
		cfg.HTTPPort = 5556
	}
	if cfg.Vips == nil {
		cfg.Vips = []VipItem{}
	}
	if len(cfg.IgnorePrefixes) == 0 {
		cfg.IgnorePrefixes = defaultConfig().IgnorePrefixes
	}
	return cfg
}

// ---------- 网卡读取 ----------

type daemon struct {
	mu   sync.RWMutex
	cfg  Config
	vips []VipItem
}

func (d *daemon) ignored(name string) bool {
	for _, p := range d.cfg.IgnorePrefixes {
		if strings.HasPrefix(name, p) {
			return true
		}
	}
	return false
}

// interfaces 读真实网卡。用 net.Interfaces 而不是 exec ip，
// 这样无需在容器里装 iproute2，也不需要 privileged 之外的特权。
func (d *daemon) interfaces() ([]InterfaceInfo, error) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	out := make([]InterfaceInfo, 0, len(ifaces))
	for _, ifc := range ifaces {
		if d.ignored(ifc.Name) {
			continue
		}
		addrs := []string{}
		if as, err := ifc.Addrs(); err == nil {
			for _, a := range as {
				addrs = append(addrs, a.String())
			}
		}
		info := InterfaceInfo{
			Name:      ifc.Name,
			Mac:       ifc.HardwareAddr.String(),
			Flags:     ifc.Flags.String(),
			Addresses: addrs,
		}
		out = append(out, info)
	}
	return out, nil
}

// ---------- HTTP ----------

func writeJSON(w http.ResponseWriter, code int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(body)
}

// notSupported 用于写接口：宁可明确失败，也不要假装成功。
func (d *daemon) notSupported(w http.ResponseWriter, op string) {
	log.Printf("拒绝写操作 %s：单节点部署不支持变更 VIP", op)
	writeJSON(w, http.StatusNotImplemented, map[string]string{
		"error": fmt.Sprintf("%s is not supported: single-node deployment, no HA VIP to manage", op),
	})
}

func (d *daemon) routes() *http.ServeMux {
	mux := http.NewServeMux()

	// GET /api/vips —— 关键：必须返回 JSON 数组（Java 侧是 List<VipItem>，不是包装对象）
	mux.HandleFunc("/api/vips", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			d.mu.RLock()
			out := append([]VipItem{}, d.vips...)
			d.mu.RUnlock()
			writeJSON(w, http.StatusOK, out)
		case http.MethodPost:
			d.notSupported(w, "set vips")
		default:
			writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		}
	})

	// POST /api/vips/add、/api/vips/delete —— 厂商 Feign 里都是 POST
	mux.HandleFunc("/api/vips/add", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
			return
		}
		d.notSupported(w, "add vip")
	})
	mux.HandleFunc("/api/vips/delete", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
			return
		}
		d.notSupported(w, "delete vip")
	})

	// GET /api/interfaces
	mux.HandleFunc("/api/interfaces", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
			return
		}
		list, err := d.interfaces()
		if err != nil {
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, list)
	})

	// 健康检查：kubelet probe 用
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	// 厂商 /api/config/sync、/api/configmap 属 HA 集群内部同步，单节点不需要；
	// 给个明确的 501，避免调用方拿到 404 后误判"服务没起来"。
	mux.HandleFunc("/api/config/sync", func(w http.ResponseWriter, r *http.Request) {
		d.notSupported(w, "config sync")
	})
	mux.HandleFunc("/api/configmap", func(w http.ResponseWriter, r *http.Request) {
		d.notSupported(w, "configmap sync")
	})
	return mux
}

func main() {
	cfgPath := flag.String("config", "/data/paas/vip/config.json", "配置 JSON 路径")
	addr := flag.String("addr", "", "监听地址，默认 :<http_port>")
	flag.Parse()

	cfg := loadConfig(*cfgPath)
	d := &daemon{cfg: cfg, vips: cfg.Vips}
	if d.vips == nil {
		d.vips = []VipItem{}
	}

	// 启动时打印一次真实状态，便于排查"为什么列表是空的"
	if list, err := d.interfaces(); err == nil {
		names := make([]string, 0, len(list))
		for _, i := range list {
			names = append(names, i.Name)
		}
		log.Printf("可达网卡: %v", names)
	}
	log.Printf("VIP 列表: %v（单节点无 HA 时为空是预期）", d.vips)

	listen := *addr
	if listen == "" {
		listen = fmt.Sprintf(":%d", cfg.HTTPPort)
	}
	srv := &http.Server{
		Addr:              listen,
		Handler:           d.routes(),
		ReadHeaderTimeout: 5 * time.Second,
	}
	log.Printf("vip-daemon 启动，监听 %s（node_id=%s, primary=%s）", listen, cfg.NodeID, cfg.PrimaryNodeID)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("监听失败: %v", err)
	}
}
