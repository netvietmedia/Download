document.addEventListener('DOMContentLoaded', () => {
  const tokenKey = 'shareit_admin_token';
  let authToken = localStorage.getItem(tokenKey);

  // Toast Notification Helper
  const showToast = (message, type = 'info') => {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    let icon = 'fa-info-circle';
    if (type === 'success') icon = 'fa-circle-check';
    if (type === 'error') icon = 'fa-circle-exclamation';
    if (type === 'warning') icon = 'fa-triangle-exclamation';

    toast.innerHTML = `<i class="fa-solid ${icon}"></i> <span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  };

  // Tab navigation titles
  const tabTitles = {
    dashboard: { title: 'Bảng Tổng Quan', desc: 'Giám sát hoạt động thời gian thực của máy chủ SHAREit (Cổng 6030)' },
    devices: { title: 'Quản Lý Thiết Bị & Bảo Mật', desc: 'Danh sách và kiểm soát các thiết bị kết nối vào hệ sinh thái' },
    mcds: { title: 'MCDS & Dynamic Remote Config', desc: 'Quản trị các vị trí hiển thị Space ID và Banner động' },
    family: { title: 'SHAREit Family & CPI Cross-Promo', desc: 'Quản lý ma trận phân phối ứng dụng chéo và quảng cáo CPI tự động' },
    ads: { title: 'Ma Trận Ad Mediation Waterfall', desc: 'Thiết lập khóa SDK toàn cục và eCPM sàn theo vị trí hiển thị' },
    plans: { title: 'Gói VIP & Quản Lý Đơn Hàng IAP', desc: 'Danh mục các gói cước thuê bao và doanh thu thời gian thực' },
    coin: { title: 'Sổ Cái Điểm Thưởng Coin & Nhiệm Vụ', desc: 'Theo dõi biến động số dư và các giao dịch cộng/trừ điểm thưởng' },
    admins: { title: 'Quản Trị Viên & Phân Quyền (RBAC)', desc: 'Quản lý danh sách tài khoản Super Admin, đổi mật khẩu và phân quyền' },
    logs: { title: 'Nhật Ký & Audit Logs', desc: 'Lịch sử thao tác quản trị và nhật ký an toàn bảo mật hệ thống' },
    tester: { title: 'API Tester Console', desc: 'Công cụ tương tác và kiểm thử trực tiếp các API trên Cổng 6030' }
  };

  const updateUserProfileUI = () => {
    const display = localStorage.getItem('shareit_admin_display') || 'Super Administrator';
    const role = localStorage.getItem('shareit_admin_role') || 'super_admin';

    const displayUserEl = document.getElementById('display-user');
    const displayRoleEl = document.getElementById('display-role');
    if (displayUserEl) displayUserEl.textContent = display;
    if (displayRoleEl) {
      let roleText = 'Super Administrator';
      if (role === 'admin') roleText = 'Administrator';
      if (role === 'moderator') roleText = 'Moderator';
      if (role === 'operator') roleText = 'Operator';
      displayRoleEl.textContent = roleText;
    }
  };

  // 1. Kiểm tra đăng nhập
  const checkAuth = () => {
    if (authToken) {
      document.getElementById('login-modal').style.display = 'none';
      document.getElementById('cms-main').style.display = 'flex';
      updateUserProfileUI();
      loadDashboard();
    } else {
      document.getElementById('login-modal').style.display = 'flex';
      document.getElementById('cms-main').style.display = 'none';
    }
  };

  // Clock updater
  setInterval(() => {
    const clockEl = document.getElementById('server-clock');
    if (clockEl) {
      clockEl.textContent = new Date().toUTCString();
    }
  }, 1000);

  // Form Đăng nhập
  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const user = document.getElementById('login-user').value;
    const pass = document.getElementById('login-pass').value;

    try {
      const res = await fetch('/api/v1/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: user, password: pass })
      });
      const data = await res.json();
      if (data.code === 0) {
        authToken = data.data.token;
        localStorage.setItem(tokenKey, authToken);
        localStorage.setItem('shareit_admin_username', data.data.username || user);
        localStorage.setItem('shareit_admin_display', data.data.display_name || 'Super Administrator');
        localStorage.setItem('shareit_admin_role', data.data.role || 'super_admin');
        localStorage.setItem('shareit_admin_id', data.data.id || 'admin_01');
        updateUserProfileUI();
        showToast(`Xin chào ${data.data.display_name || user}! Đăng nhập CMS thành công!`, 'success');
        checkAuth();
      } else {
        showToast(data.message || 'Sai tên đăng nhập hoặc mật khẩu', 'error');
      }
    } catch (err) {
      showToast('Lỗi kết nối máy chủ: ' + err.message, 'error');
    }
  });

  // Đăng xuất
  document.getElementById('btn-logout').addEventListener('click', () => {
    localStorage.removeItem(tokenKey);
    localStorage.removeItem('shareit_admin_username');
    localStorage.removeItem('shareit_admin_display');
    localStorage.removeItem('shareit_admin_role');
    localStorage.removeItem('shareit_admin_id');
    authToken = null;
    showToast('Đã đăng xuất khỏi phiên làm việc', 'info');
    checkAuth();
  });

  // Tab Navigation Switching
  const navButtons = document.querySelectorAll('.nav-item');
  navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      navButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const tabId = btn.getAttribute('data-tab');
      document.querySelectorAll('.tab-pane').forEach(pane => pane.classList.remove('active'));
      document.getElementById(`tab-${tabId}`)?.classList.add('active');

      // Update Header title
      if (tabTitles[tabId]) {
        document.getElementById('current-tab-title').textContent = tabTitles[tabId].title;
        document.getElementById('current-tab-desc').textContent = tabTitles[tabId].desc;
      }

      // Load specific tab data
      if (tabId === 'dashboard') loadDashboard();
      if (tabId === 'devices') loadDevices();
      if (tabId === 'mcds') loadMCDS();
      if (tabId === 'family') loadFamily();
      if (tabId === 'ads') loadAds();
      if (tabId === 'plans') loadPlans();
      if (tabId === 'coin') loadCoin();
      if (tabId === 'admins') loadAdmins();
      if (tabId === 'logs') loadLogs();
    });
  });

  // Helper fetch with admin token
  const apiFetch = async (url, options = {}) => {
    const headers = {
      'Content-Type': 'application/json',
      'x-admin-token': authToken,
      ...(options.headers || {})
    };
    return fetch(url, { ...options, headers });
  };

  // 2. Load Dashboard Data
  let trafficChartInstance = null;
  const loadDashboard = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/stats');
      const json = await res.json();
      if (json.code === 0) {
        const m = json.data.metrics;
        document.getElementById('kpi-devices').textContent = m.total_devices;
        document.getElementById('kpi-sessions').textContent = m.active_p2p_sessions + m.connected_peers;
        document.getElementById('kpi-vips').textContent = m.total_vips;
        document.getElementById('kpi-revenue').textContent = `$${m.total_revenue.toFixed(2)}`;

        // Render Recent Devices table
        const tbody = document.querySelector('#table-recent-devices tbody');
        tbody.innerHTML = '';
        json.data.recent_devices.forEach(d => {
          tbody.innerHTML += `
            <tr>
              <td><code>${d.device_uuid}</code></td>
              <td><i class="fa-brands fa-${d.os_type === 'android' ? 'android' : 'apple'}" style="color:#38bdf8;"></i> ${d.os_type} ${d.os_version}</td>
              <td>${d.manufacturer} ${d.device_model}</td>
              <td><b>${d.country_code}</b></td>
              <td>${new Date(d.last_active_at).toLocaleTimeString()}</td>
              <td><span class="${d.is_banned ? 'badge-banned' : 'badge-active'}">${d.is_banned ? 'Bị khóa' : 'Hoạt động'}</span></td>
            </tr>
          `;
        });

        // Render Chart
        renderChart();
      }
    } catch (e) {
      console.error('Error loading dashboard:', e);
    }
  };

  const renderChart = () => {
    const ctx = document.getElementById('trafficChart');
    if (!ctx) return;

    if (trafficChartInstance) {
      trafficChartInstance.destroy();
    }

    trafficChartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels: ['06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00'],
        datasets: [
          {
            label: 'Wi-Fi Direct P2P (MB/s)',
            data: [45, 120, 240, 190, 280, 310, 420, 380, 290],
            borderColor: '#38bdf8',
            backgroundColor: 'rgba(56, 189, 248, 0.1)',
            fill: true,
            tension: 0.4
          },
          {
            label: 'Cloud Relay Chunk Stream (MB/s)',
            data: [15, 30, 80, 65, 95, 110, 140, 125, 90],
            borderColor: '#f59e0b',
            backgroundColor: 'rgba(245, 158, 11, 0.05)',
            fill: true,
            tension: 0.4
          }
        ]
      },
      options: {
        responsive: true,
        plugins: {
          legend: { labels: { color: '#94a3b8' } }
        },
        scales: {
          x: { grid: { color: '#1f293d' }, ticks: { color: '#94a3b8' } },
          y: { grid: { color: '#1f293d' }, ticks: { color: '#94a3b8' } }
        }
      }
    });
  };

  // 3. Load Devices
  let cachedDevices = [];
  const loadDevices = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/devices');
      const json = await res.json();
      cachedDevices = json.data || [];
      renderDevicesList(cachedDevices);
    } catch (e) {
      console.error(e);
    }
  };

  const renderDevicesList = (list) => {
    const tbody = document.querySelector('#table-all-devices tbody');
    tbody.innerHTML = '';
    list.forEach(d => {
      tbody.innerHTML += `
        <tr>
          <td><code>${d.device_uuid}</code></td>
          <td><i class="fa-brands fa-${d.os_type === 'android' ? 'android' : 'apple'}" style="color:#38bdf8;"></i> ${d.os_type} ${d.os_version}</td>
          <td><span class="badge-active" style="background:#1e293b;color:#38bdf8;border-color:#334155;">${d.app_version}</span></td>
          <td>${d.manufacturer} ${d.device_model}</td>
          <td><code style="font-size:11px;color:#94a3b8;">${d.gaid_idfv || 'N/A'}</code></td>
          <td><b>${d.country_code}</b></td>
          <td>
            <button class="btn-secondary btn-ban" data-uuid="${d.device_uuid}">
              <i class="fa-solid ${d.is_banned ? 'fa-lock-open' : 'fa-ban'}"></i> ${d.is_banned ? 'Mở Khóa' : 'Khóa'}
            </button>
          </td>
        </tr>
      `;
    });

    document.querySelectorAll('.btn-ban').forEach(btn => {
      btn.addEventListener('click', async () => {
        const uuid = btn.getAttribute('data-uuid');
        await apiFetch('/api/v1/admin/devices/toggle-ban', {
          method: 'POST',
          body: JSON.stringify({ device_uuid: uuid })
        });
        showToast(`Đã thay đổi trạng thái thiết bị [${uuid.slice(0, 16)}...]`, 'success');
        loadDevices();
      });
    });
  };

  // Device Search Filter
  document.getElementById('device-search')?.addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    if (!q) return renderDevicesList(cachedDevices);
    const filtered = cachedDevices.filter(d => 
      (d.device_uuid && d.device_uuid.toLowerCase().includes(q)) ||
      (d.device_model && d.device_model.toLowerCase().includes(q)) ||
      (d.country_code && d.country_code.toLowerCase().includes(q))
    );
    renderDevicesList(filtered);
  });

  // 4. Load MCDS
  const loadMCDS = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/mcds');
      const json = await res.json();
      const container = document.getElementById('mcds-banner-list');
      container.innerHTML = '';

      json.data.promotes.forEach(p => {
        container.innerHTML += `
          <div class="banner-item">
            <img src="${p.media_url}" alt="${p.title}" onerror="this.src='https://placehold.co/600x300/1e293b/38bdf8?text=Banner+Image'">
            <div class="banner-content">
              <h4>${p.title}</h4>
              <p style="font-size:11px;color:#94a3b8;margin-bottom:8px;">Space: <b>${p.space_id}</b> | Action: <code>${p.action_value}</code></p>
              <button class="btn-danger btn-del-prom" data-id="${p.id}">
                <i class="fa-solid fa-trash"></i> Xóa Banner
              </button>
            </div>
          </div>
        `;
      });

      document.querySelectorAll('.btn-del-prom').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          await apiFetch(`/api/v1/admin/mcds/promotes/${id}`, { method: 'DELETE' });
          showToast('Đã gỡ banner MCDS thành công', 'info');
          loadMCDS();
        });
      });
    } catch (e) {
      console.error(e);
    }
  };

  // Form Create MCDS Banner
  document.getElementById('form-create-banner')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      space_id: document.getElementById('mcds-space-select').value,
      title: document.getElementById('mcds-title').value,
      content_type: document.getElementById('mcds-type').value,
      media_url: document.getElementById('mcds-url').value,
      action_type: 'deeplink',
      action_value: document.getElementById('mcds-action').value,
      priority: document.getElementById('mcds-priority').value
    };

    await apiFetch('/api/v1/admin/mcds/promotes', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    showToast('Đã xuất bản banner MCDS mới thành công!', 'success');
    document.getElementById('mcds-title').value = '';
    loadMCDS();
  });

  // 4b. Load SHAREit Family & CPI Cross-Promo
  const loadFamily = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/family-products');
      const json = await res.json();
      const container = document.getElementById('family-products-list');
      if (!container) return;
      container.innerHTML = '';

      const products = json.data || [];
      const activeCount = products.filter(p => p.is_active).length;

      document.getElementById('kpi-family-total').textContent = products.length;
      document.getElementById('kpi-family-active').textContent = activeCount;
      document.getElementById('family-count-badge').textContent = `${activeCount} Apps Active`;

      if (products.length > 0) {
        const avgCpi = products.reduce((acc, p) => acc + (p.cpi_payout || 0), 0) / products.length;
        document.getElementById('kpi-family-cpi').textContent = `$${avgCpi.toFixed(2)}`;
      }

      products.forEach(p => {
        container.innerHTML += `
          <div class="family-card" style="opacity: ${p.is_active ? '1' : '0.6'};">
            <div class="family-card-header">
              <img src="${p.icon_url}" alt="${p.app_name}" onerror="this.src='https://placehold.co/100x100/1e293b/38bdf8?text=App'">
              <div class="family-app-info">
                <div class="family-title-row">
                  <h4>${p.app_name}</h4>
                  <span class="family-badge badge-${p.badge}">${p.badge}</span>
                </div>
                <span class="category"><i class="fa-solid fa-folder"></i> ${p.category}</span>
                <span class="category" style="font-family:monospace;font-size:10px;color:#38bdf8;">${p.package_name}</span>
              </div>
            </div>

            <div class="family-meta">
              <span class="rating"><i class="fa-solid fa-star"></i> ${p.rating}</span>
              <span class="downloads"><i class="fa-solid fa-download"></i> ${p.download_count_str}</span>
              <span class="family-cpi">CPI: $${p.cpi_payout.toFixed(2)}</span>
            </div>

            <p class="family-desc">${p.description}</p>

            <div class="family-actions">
              <button class="btn-secondary btn-toggle-fam" data-id="${p.id}" data-active="${p.is_active}">
                <i class="fa-solid ${p.is_active ? 'fa-pause' : 'fa-play'}"></i> ${p.is_active ? 'Tạm Dừng' : 'Kích Hoạt'}
              </button>
              <button class="btn-danger btn-del-fam" data-id="${p.id}">
                <i class="fa-solid fa-trash"></i> Xóa
              </button>
            </div>
          </div>
        `;
      });

      // Toggle Active Event
      document.querySelectorAll('.btn-toggle-fam').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          const currentActive = btn.getAttribute('data-active') === 'true';
          await apiFetch(`/api/v1/admin/family-products/${id}`, {
            method: 'PUT',
            body: JSON.stringify({ is_active: !currentActive })
          });
          showToast('Đã cập nhật trạng thái phân phối ứng dụng', 'success');
          loadFamily();
        });
      });

      // Delete Event
      document.querySelectorAll('.btn-del-fam').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          await apiFetch(`/api/v1/admin/family-products/${id}`, { method: 'DELETE' });
          showToast('Đã xóa ứng dụng khỏi SHAREit Family', 'info');
          loadFamily();
        });
      });

    } catch (e) {
      console.error('Error loading family products:', e);
    }
  };

  // Form Create Family Product
  const formCreateFam = document.getElementById('form-create-family');
  if (formCreateFam) {
    formCreateFam.addEventListener('submit', async (e) => {
      e.preventDefault();
      const payload = {
        app_name: document.getElementById('fam-name').value,
        package_name: document.getElementById('fam-package').value,
        icon_url: document.getElementById('fam-icon').value,
        download_url: document.getElementById('fam-download').value,
        category: document.getElementById('fam-category').value,
        badge: document.getElementById('fam-badge').value,
        rating: document.getElementById('fam-rating').value,
        download_count_str: document.getElementById('fam-downloads').value,
        description: document.getElementById('fam-desc').value,
        cpi_payout: document.getElementById('fam-cpi').value,
        priority: document.getElementById('fam-priority').value,
        is_active: true
      };

      await apiFetch('/api/v1/admin/family-products', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showToast('Đã thêm ứng dụng mới vào SHAREit Family thành công!', 'success');
      document.getElementById('fam-name').value = '';
      document.getElementById('fam-package').value = '';
      document.getElementById('fam-desc').value = '';
      loadFamily();
    });
  }

  // 5. Load Ads Waterfall & Global Keys
  const loadAds = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/ads');
      const json = await res.json();
      const container = document.getElementById('ad-placements-container');
      container.innerHTML = '';

      const { global_keys, placements } = json.data;

      // Fill Global Keys Form
      if (global_keys) {
        document.getElementById('ad-key-admob').value = global_keys.admob_app_id || '';
        document.getElementById('ad-key-applovin').value = global_keys.applovin_sdk_key || '';
        document.getElementById('ad-key-mintegral-id').value = global_keys.mintegral_app_id || '';
        document.getElementById('ad-key-mintegral-key').value = global_keys.mintegral_app_key || '';
        document.getElementById('ad-key-unity').value = global_keys.unity_game_id || '';
        document.getElementById('ad-key-pangle').value = global_keys.pangle_app_id || '';
        document.getElementById('ad-test-mode').checked = Boolean(global_keys.test_mode);
      }

      // Render Placements
      placements.forEach((p, pIdx) => {
        let waterfallRows = '';
        p.mediation_waterfall.forEach((w, wIdx) => {
          waterfallRows += `
            <tr data-tier="${wIdx}">
              <td><span class="tier-badge">Tier ${wIdx + 1}</span></td>
              <td>
                <select class="wf-network input-sm" style="padding:4px 8px;background:#0b1120;color:#fff;border:1px solid #334155;border-radius:4px;">
                  <option value="admob" ${w.network === 'admob' ? 'selected' : ''}>Google AdMob</option>
                  <option value="applovin" ${w.network === 'applovin' ? 'selected' : ''}>AppLovin MAX</option>
                  <option value="mintegral" ${w.network === 'mintegral' ? 'selected' : ''}>Mintegral</option>
                  <option value="unity" ${w.network === 'unity' ? 'selected' : ''}>Unity Ads</option>
                  <option value="pangle" ${w.network === 'pangle' ? 'selected' : ''}>Pangle</option>
                </select>
              </td>
              <td>
                <input type="text" class="wf-unit-id" value="${w.unit_id}" style="width:100%;padding:4px 8px;font-family:monospace;font-size:12px;background:#0b1120;color:#38bdf8;border:1px solid #334155;border-radius:4px;">
              </td>
              <td>
                <input type="number" step="0.1" class="wf-floor" value="${w.ecpm_floor}" style="width:70px;padding:4px 6px;background:#0b1120;color:#f59e0b;border:1px solid #334155;border-radius:4px;">
              </td>
              <td>
                <input type="number" step="100" class="wf-timeout" value="${w.timeout_ms}" style="width:70px;padding:4px 6px;background:#0b1120;color:#94a3b8;border:1px solid #334155;border-radius:4px;">
              </td>
              <td>
                <button type="button" class="btn-del-tier" style="background:transparent;border:none;color:#ef4444;cursor:pointer;"><i class="fa-solid fa-trash"></i></button>
              </td>
            </tr>
          `;
        });

        container.innerHTML += `
          <div class="placement-card" style="opacity: ${p.is_active ? '1' : '0.6'};">
            <div class="placement-header">
              <h4>
                <i class="fa-solid fa-layer-group" style="color:#38bdf8;"></i>
                <code>${p.placement_id}</code>
                <span class="badge-active" style="text-transform:uppercase;font-size:10px;">${p.placement_type}</span>
                <span class="${p.is_active ? 'badge-active' : 'badge-banned'}" style="font-size:10px;">${p.is_active ? 'Active' : 'Paused'}</span>
              </h4>
              <div class="placement-actions">
                <button class="btn-secondary btn-toggle-plc" data-pid="${p.placement_id}" data-active="${p.is_active}">
                  <i class="fa-solid ${p.is_active ? 'fa-pause' : 'fa-play'}"></i> ${p.is_active ? 'Tạm Dừng' : 'Kích Hoạt'}
                </button>
                <button class="btn-secondary btn-add-tier" data-pid="${p.placement_id}">
                  <i class="fa-solid fa-plus"></i> Thêm Tier
                </button>
                <button class="btn-primary btn-save-waterfall" data-pid="${p.placement_id}">
                  <i class="fa-solid fa-floppy-disk"></i> Lưu Waterfall
                </button>
                <button class="btn-danger btn-del-plc" data-pid="${p.placement_id}" title="Xóa placement">
                  <i class="fa-solid fa-trash"></i>
                </button>
              </div>
            </div>

            <table class="waterfall-table">
              <thead>
                <tr>
                  <th style="width:80px;">Thứ Tự</th>
                  <th style="width:160px;">Mạng QC</th>
                  <th>Ad Unit ID / Placement Token</th>
                  <th style="width:100px;">eCPM Sàn ($)</th>
                  <th style="width:100px;">Timeout (ms)</th>
                  <th style="width:40px;"></th>
                </tr>
              </thead>
              <tbody id="wf-rows-${p.placement_id}">${waterfallRows}</tbody>
            </table>
          </div>
        `;
      });

      // Bind Placement Save Waterfall Events
      document.querySelectorAll('.btn-save-waterfall').forEach(btn => {
        btn.addEventListener('click', async () => {
          const pid = btn.getAttribute('data-pid');
          const rows = document.querySelectorAll(`#wf-rows-${pid} tr`);
          const newWaterfall = [];

          rows.forEach(r => {
            const net = r.querySelector('.wf-network').value;
            const unit = r.querySelector('.wf-unit-id').value;
            const floor = parseFloat(r.querySelector('.wf-floor').value || '0');
            const timeout = parseInt(r.querySelector('.wf-timeout').value || '2500', 10);
            newWaterfall.push({ network: net, unit_id: unit, ecpm_floor: floor, timeout_ms: timeout, is_active: true });
          });

          await apiFetch('/api/v1/admin/ads/update', {
            method: 'POST',
            body: JSON.stringify({ placement_id: pid, waterfall: newWaterfall })
          });
          showToast(`Đã lưu ma trận Waterfall cho [${pid}] thành công!`, 'success');
          loadAds();
        });
      });

      // Bind Toggle Placement Active
      document.querySelectorAll('.btn-toggle-plc').forEach(btn => {
        btn.addEventListener('click', async () => {
          const pid = btn.getAttribute('data-pid');
          const currentActive = btn.getAttribute('data-active') === 'true';
          await apiFetch('/api/v1/admin/ads/update', {
            method: 'POST',
            body: JSON.stringify({ placement_id: pid, is_active: !currentActive })
          });
          showToast(`Đã cập nhật trạng thái placement [${pid}]`, 'info');
          loadAds();
        });
      });

      // Bind Delete Placement
      document.querySelectorAll('.btn-del-plc').forEach(btn => {
        btn.addEventListener('click', async () => {
          const pid = btn.getAttribute('data-pid');
          await apiFetch(`/api/v1/admin/ads/placements/${pid}`, { method: 'DELETE' });
          showToast(`Đã xóa vị trí quảng cáo [${pid}]`, 'info');
          loadAds();
        });
      });

      // Bind Add Tier
      document.querySelectorAll('.btn-add-tier').forEach(btn => {
        btn.addEventListener('click', () => {
          const pid = btn.getAttribute('data-pid');
          const tbody = document.getElementById(`wf-rows-${pid}`);
          const count = tbody.querySelectorAll('tr').length;
          const newRow = document.createElement('tr');
          newRow.innerHTML = `
            <td><span class="tier-badge">Tier ${count + 1}</span></td>
            <td>
              <select class="wf-network" style="padding:4px 8px;background:#0b1120;color:#fff;border:1px solid #334155;border-radius:4px;">
                <option value="admob">Google AdMob</option>
                <option value="applovin" selected>AppLovin MAX</option>
                <option value="mintegral">Mintegral</option>
                <option value="unity">Unity Ads</option>
                <option value="pangle">Pangle</option>
              </select>
            </td>
            <td>
              <input type="text" class="wf-unit-id" value="new_ad_unit_id_${count + 1}" style="width:100%;padding:4px 8px;font-family:monospace;font-size:12px;background:#0b1120;color:#38bdf8;border:1px solid #334155;border-radius:4px;">
            </td>
            <td>
              <input type="number" step="0.1" class="wf-floor" value="1.50" style="width:70px;padding:4px 6px;background:#0b1120;color:#f59e0b;border:1px solid #334155;border-radius:4px;">
            </td>
            <td>
              <input type="number" step="100" class="wf-timeout" value="2500" style="width:70px;padding:4px 6px;background:#0b1120;color:#94a3b8;border:1px solid #334155;border-radius:4px;">
            </td>
            <td>
              <button type="button" class="btn-del-tier" style="background:transparent;border:none;color:#ef4444;cursor:pointer;"><i class="fa-solid fa-trash"></i></button>
            </td>
          `;
          newRow.querySelector('.btn-del-tier').addEventListener('click', () => newRow.remove());
          tbody.appendChild(newRow);
        });
      });

      // Bind Delete Tier Event
      document.querySelectorAll('.btn-del-tier').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const tr = e.target.closest('tr');
          if (tr) tr.remove();
        });
      });

    } catch (e) {
      console.error('Error loading ads mediation:', e);
    }
  };

  // Form Global Ad Keys Submit
  const formGlobalKeys = document.getElementById('form-global-ad-keys');
  if (formGlobalKeys) {
    formGlobalKeys.addEventListener('submit', async (e) => {
      e.preventDefault();
      const payload = {
        admob_app_id: document.getElementById('ad-key-admob').value,
        applovin_sdk_key: document.getElementById('ad-key-applovin').value,
        mintegral_app_id: document.getElementById('ad-key-mintegral-id').value,
        mintegral_app_key: document.getElementById('ad-key-mintegral-key').value,
        unity_game_id: document.getElementById('ad-key-unity').value,
        pangle_app_id: document.getElementById('ad-key-pangle').value,
        test_mode: document.getElementById('ad-test-mode').checked
      };

      await apiFetch('/api/v1/admin/ads/global-keys', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showToast('Đã cập nhật và đồng bộ Khóa SDK Quảng Cáo Toàn Cục thành công!', 'success');
      loadAds();
    });
  }

  // Toggle Show/Hide Add Placement Form
  document.getElementById('btn-show-add-placement')?.addEventListener('click', () => {
    document.getElementById('add-placement-box').style.display = 'block';
  });
  document.getElementById('btn-cancel-add-placement')?.addEventListener('click', () => {
    document.getElementById('add-placement-box').style.display = 'none';
  });

  // Form Create Placement
  document.getElementById('form-create-placement')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const pid = document.getElementById('new-plc-id').value.trim();
    const ptype = document.getElementById('new-plc-type').value;

    const res = await apiFetch('/api/v1/admin/ads/placements', {
      method: 'POST',
      body: JSON.stringify({ placement_id: pid, placement_type: ptype })
    });
    const json = await res.json();
    if (json.code === 0) {
      showToast(`Đã thêm vị trí quảng cáo [${pid}]!`, 'success');
      document.getElementById('new-plc-id').value = '';
      document.getElementById('add-placement-box').style.display = 'none';
      loadAds();
    } else {
      showToast(json.message || 'Lỗi thêm placement', 'error');
    }
  });

  // 6. Load Plans
  const loadPlans = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/plans');
      const json = await res.json();
      const container = document.getElementById('plans-container');
      container.innerHTML = '';

      json.data.forEach(p => {
        container.innerHTML += `
          <div class="plan-card ${p.id === 'vip_yearly' ? 'featured' : ''}">
            <h3>${p.title}</h3>
            <div class="plan-price">$${p.price} <span style="font-size:13px;color:#94a3b8;">/ ${p.duration_days} ngày</span></div>
            <p style="font-size:11px;color:#38bdf8;">Google SKU: <code>${p.google_product_id}</code></p>
            <ul class="plan-features">
              ${p.features.map(f => `<li><i class="fa-solid fa-check" style="color:#10b981;"></i> ${f}</li>`).join('')}
            </ul>
          </div>
        `;
      });

      // Load IAP orders
      const statsRes = await apiFetch('/api/v1/admin/stats');
      const statsJson = await statsRes.json();
      const tbody = document.querySelector('#table-iap-orders tbody');
      tbody.innerHTML = '';
      statsJson.data.recent_orders.forEach(o => {
        tbody.innerHTML += `
          <tr>
            <td><code>${o.order_code}</code></td>
            <td><b>${o.plan_id}</b></td>
            <td>${o.gateway}</td>
            <td><b style="color:#10b981;">$${o.amount} ${o.currency}</b></td>
            <td><span class="badge-active">${o.status.toUpperCase()}</span></td>
            <td>${new Date(o.created_at).toLocaleString()}</td>
          </tr>
        `;
      });
    } catch (e) {
      console.error(e);
    }
  };

  // 7. Load Coin Economy & Gamification Center
  const loadCoin = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/coin/overview');
      const json = await res.json();
      if (json.code !== 0) return;

      const { stats, tasks, rewards, redemptions, ledger } = json.data;

      // KPIs
      document.getElementById('kpi-coin-issued').textContent = (stats.total_issued || 0).toLocaleString() + ' Coin';
      document.getElementById('kpi-coin-circ').textContent = (stats.total_circulation || 0).toLocaleString() + ' Coin';
      document.getElementById('kpi-coin-redeemed').textContent = (stats.total_redeemed || 0).toLocaleString() + ' Coin';
      document.getElementById('kpi-coin-pending').textContent = stats.pending_redemptions || 0;

      // Update sidebar counter badge
      const sidebarBadge = document.getElementById('sidebar-coin-badge');
      if (sidebarBadge) {
        if (stats.pending_redemptions > 0) {
          sidebarBadge.textContent = stats.pending_redemptions;
          sidebarBadge.style.display = 'inline-block';
        } else {
          sidebarBadge.style.display = 'none';
        }
      }

      // 7a. Render Tasks
      const tbodyTasks = document.querySelector('#table-coin-tasks tbody');
      if (tbodyTasks) {
        tbodyTasks.innerHTML = '';
        (tasks || []).forEach(t => {
          tbodyTasks.innerHTML += `
            <tr>
              <td>
                <div style="display:flex;align-items:center;gap:10px;">
                  <img src="${t.icon_url}" style="width:32px;height:32px;border-radius:6px;" onerror="this.src='https://placehold.co/50x50/1e293b/38bdf8?text=Task'">
                  <div>
                    <b style="color:#fff;">${t.title}</b>
                    <div style="font-size:11px;color:#94a3b8;">${t.description}</div>
                  </div>
                </div>
              </td>
              <td><span class="badge-active" style="text-transform:uppercase;">${t.category || 'transfer'}</span></td>
              <td><b style="color:#f59e0b;">+${t.reward_coins || 50} Coin</b></td>
              <td>${t.daily_limit || 1} lượt/ngày</td>
              <td><span class="${t.is_active ? 'badge-active' : 'badge-banned'}">${t.is_active ? 'Active' : 'Off'}</span></td>
              <td>
                <button class="btn-danger btn-del-task" data-id="${t.id}" style="padding:4px 8px;font-size:11px;"><i class="fa-solid fa-trash"></i></button>
              </td>
            </tr>
          `;
        });

        document.querySelectorAll('.btn-del-task').forEach(btn => {
          btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            await apiFetch(`/api/v1/admin/coin/tasks/${id}`, { method: 'DELETE' });
            showToast('Đã xóa nhiệm vụ', 'info');
            loadCoin();
          });
        });
      }

      // 7b. Render Rewards Shop
      const rewContainer = document.getElementById('coin-rewards-list');
      if (rewContainer) {
        rewContainer.innerHTML = '';
        (rewards || []).forEach(r => {
          rewContainer.innerHTML += `
            <div class="reward-card">
              <div class="reward-card-header">
                <img src="${r.icon_url}" onerror="this.src='https://placehold.co/80x80/1e293b/f59e0b?text=Gift'">
                <div>
                  <h4 style="font-size:14px;color:#fff;">${r.title}</h4>
                  <span style="font-size:11px;color:#38bdf8;">${r.payout_value_str}</span>
                </div>
              </div>
              <div class="reward-cost"><i class="fa-solid fa-coins"></i> ${r.cost_coins.toLocaleString()} Coin</div>
              <p style="font-size:12px;color:#94a3b8;flex:1;margin-bottom:12px;">${r.description}</p>
              <div style="display:flex;justify-content:space-between;align-items:center;border-top:1px solid #1f293d;padding-top:10px;">
                <span style="font-size:11px;color:#94a3b8;">Kho: <b>${r.stock}</b></span>
                <button class="btn-danger btn-del-reward" data-id="${r.id}" style="padding:4px 8px;font-size:11px;"><i class="fa-solid fa-trash"></i> Xóa</button>
              </div>
            </div>
          `;
        });

        document.querySelectorAll('.btn-del-reward').forEach(btn => {
          btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            await apiFetch(`/api/v1/admin/coin/rewards/${id}`, { method: 'DELETE' });
            showToast('Đã xóa vật phẩm đổi thưởng', 'info');
            loadCoin();
          });
        });
      }

      // 7c. Render Redemptions Approval
      const tbodyRed = document.querySelector('#table-coin-redemptions tbody');
      if (tbodyRed) {
        tbodyRed.innerHTML = '';
        (redemptions || []).forEach(red => {
          let statusBadge = `<span class="badge-active">${red.status.toUpperCase()}</span>`;
          if (red.status === 'pending') statusBadge = `<span class="badge-warning">CHỜ DUYỆT</span>`;
          if (red.status === 'rejected') statusBadge = `<span class="badge-banned">TỪ CHỐI</span>`;

          let actionBtns = '-';
          if (red.status === 'pending') {
            actionBtns = `
              <button class="btn-approve" data-id="${red.id}"><i class="fa-solid fa-check"></i> Duyệt</button>
              <button class="btn-reject" data-id="${red.id}"><i class="fa-solid fa-xmark"></i> Từ Chối</button>
            `;
          }

          tbodyRed.innerHTML += `
            <tr>
              <td><code>${red.request_code}</code></td>
              <td><b>${red.reward_title}</b></td>
              <td><b style="color:#ef4444;">-${red.coins_spent} Coin</b></td>
              <td><code style="color:#38bdf8;">${red.payout_target}</code></td>
              <td>${statusBadge}</td>
              <td>${new Date(red.created_at).toLocaleString()}</td>
              <td>${actionBtns}</td>
            </tr>
          `;
        });

        // Redemptions action events
        document.querySelectorAll('.btn-approve').forEach(btn => {
          btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            await apiFetch(`/api/v1/admin/coin/redemptions/${id}/process`, {
              method: 'POST',
              body: JSON.stringify({ action: 'approve' })
            });
            showToast('Đã phê duyệt trao thưởng thành công!', 'success');
            loadCoin();
          });
        });

        document.querySelectorAll('.btn-reject').forEach(btn => {
          btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            await apiFetch(`/api/v1/admin/coin/redemptions/${id}/process`, {
              method: 'POST',
              body: JSON.stringify({ action: 'reject', admin_note: 'Yêu cầu không hợp lệ - Hoàn xu' })
            });
            showToast('Đã từ chối đơn và hoàn trả coin về ví người dùng!', 'info');
            loadCoin();
          });
        });
      }

      // 7d. Render Ledger
      const tbodyLedger = document.querySelector('#table-coin-ledger tbody');
      if (tbodyLedger) {
        tbodyLedger.innerHTML = '';
        (ledger || []).forEach(tx => {
          tbodyLedger.innerHTML += `
            <tr>
              <td><code>${tx.transaction_id}</code></td>
              <td>${tx.user_id} <span style="font-size:11px;color:#64748b;">(${tx.device_uuid})</span></td>
              <td><span class="badge-active" style="background:#1e293b;border-color:#334155;color:#38bdf8;">${tx.event_type}</span></td>
              <td style="font-weight:700;color:${tx.amount > 0 ? '#10b981' : '#ef4444'};">${tx.amount > 0 ? '+' : ''}${tx.amount} Coin</td>
              <td><b>${tx.balance_after} Coin</b></td>
              <td>${new Date(tx.created_at).toLocaleTimeString()}</td>
            </tr>
          `;
        });
      }

    } catch (e) {
      console.error('Error loading coin economy:', e);
    }
  };

  // Coin Sub-Tabs Event Switching
  document.querySelectorAll('.coin-tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.coin-tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const sub = btn.getAttribute('data-sub');
      document.querySelectorAll('.coin-sub-pane').forEach(p => p.style.display = 'none');
      document.getElementById(`coin-sub-${sub}`).style.display = 'block';
    });
  });

  // Form Create Coin Task
  document.getElementById('form-create-task')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      title: document.getElementById('task-title').value,
      description: document.getElementById('task-desc').value,
      reward_coins: document.getElementById('task-reward').value,
      category: document.getElementById('task-category').value,
      daily_limit: document.getElementById('task-limit').value,
      action_deeplink: document.getElementById('task-deeplink').value
    };

    await apiFetch('/api/v1/admin/coin/tasks', { method: 'POST', body: JSON.stringify(payload) });
    showToast('Đã xuất bản nhiệm vụ kiếm coin mới!', 'success');
    document.getElementById('task-title').value = '';
    document.getElementById('task-desc').value = '';
    loadCoin();
  });

  // Form Create Coin Reward
  document.getElementById('form-create-reward')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      title: document.getElementById('rew-title').value,
      category: document.getElementById('rew-category').value,
      cost_coins: document.getElementById('rew-cost').value,
      payout_value_str: document.getElementById('rew-val-str').value,
      stock: document.getElementById('rew-stock').value,
      icon_url: document.getElementById('rew-icon').value,
      description: document.getElementById('rew-desc').value
    };

    await apiFetch('/api/v1/admin/coin/rewards', { method: 'POST', body: JSON.stringify(payload) });
    showToast('Đã thêm quà tặng vào Cửa hàng Coin!', 'success');
    document.getElementById('rew-title').value = '';
    document.getElementById('rew-desc').value = '';
    loadCoin();
  });

  // Form Manual Adjust Coin
  document.getElementById('form-adjust-coin')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      identifier: document.getElementById('adj-user').value.trim(),
      amount: document.getElementById('adj-amount').value,
      reason: document.getElementById('adj-reason').value
    };

    const res = await apiFetch('/api/v1/admin/coin/adjust', { method: 'POST', body: JSON.stringify(payload) });
    const json = await res.json();
    if (json.code === 0) {
      showToast(`Đã điều chỉnh ${payload.amount} xu cho [${payload.identifier}] thành công!`, 'success');
      document.getElementById('adj-amount').value = '';
      document.getElementById('adj-reason').value = '';
      loadCoin();
    } else {
      showToast(json.message || 'Lỗi điều chỉnh coin', 'error');
    }
  });

  // 8. Load Logs
  let cachedLogs = [];
  const loadLogs = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/logs');
      const json = await res.json();
      cachedLogs = json.data || [];
      renderLogs(cachedLogs);
    } catch (e) {
      console.error(e);
    }
  };

  const renderLogs = (logs) => {
    const tbody = document.querySelector('#table-audit-logs tbody');
    tbody.innerHTML = '';
    logs.forEach(l => {
      tbody.innerHTML += `
        <tr>
          <td>${new Date(l.created_at).toLocaleString()}</td>
          <td><span class="badge-active" style="background:#1e293b;border-color:#334155;color:#38bdf8;">${l.action}</span></td>
          <td><b>${l.resource}</b></td>
          <td>${l.details}</td>
          <td><code>${l.ip_address}</code></td>
          <td><span class="badge-active">${l.admin_user}</span></td>
        </tr>
      `;
    });
  };

  document.getElementById('log-search')?.addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    if (!q) return renderLogs(cachedLogs);
    const filtered = cachedLogs.filter(l => 
      (l.action && l.action.toLowerCase().includes(q)) ||
      (l.resource && l.resource.toLowerCase().includes(q)) ||
      (l.details && l.details.toLowerCase().includes(q)) ||
      (l.ip_address && l.ip_address.toLowerCase().includes(q))
    );
    renderLogs(filtered);
  });

  // 9. API Tester Console
  const apiPresets = {
    mcds_fetch: {
      method: 'POST',
      url: '/api/v1/mcds/fetch',
      body: JSON.stringify({ space_ids: ['home_top_banner'], context: { country: 'VN' } }, null, 2)
    },
    ads_strategy: {
      method: 'POST',
      url: '/api/v1/ads/strategy/get',
      body: JSON.stringify({ placement_id: 'splash_interstitial', user_id: 'u_tester_01', country: 'VN' }, null, 2)
    },
    healthz: {
      method: 'GET',
      url: '/healthz',
      body: '{}'
    },
    coin_balance: {
      method: 'GET',
      url: '/api/v1/coin/balance?user_id=usr_01',
      body: '{}'
    },
    signaling_ping: {
      method: 'GET',
      url: '/ping/',
      body: '{}'
    }
  };

  document.getElementById('tester-presets')?.addEventListener('change', (e) => {
    const p = apiPresets[e.target.value];
    if (p) {
      document.getElementById('tester-method').value = p.method;
      document.getElementById('tester-url').value = p.url;
      document.getElementById('tester-body').value = p.body;
    }
  });

  document.getElementById('btn-send-api')?.addEventListener('click', async () => {
    const method = document.getElementById('tester-method').value;
    const url = document.getElementById('tester-url').value;
    const bodyText = document.getElementById('tester-body').value;
    const resEl = document.getElementById('tester-response');
    const latencyEl = document.getElementById('tester-latency');

    resEl.textContent = '// Đang gửi yêu cầu...';
    latencyEl.textContent = '...';
    const startTime = performance.now();

    try {
      const options = {
        method,
        headers: { 'Content-Type': 'application/json', 'x-admin-token': authToken }
      };
      if (method !== 'GET' && method !== 'HEAD' && bodyText.trim()) {
        options.body = bodyText;
      }
      const response = await fetch(url, options);
      const elapsed = Math.round(performance.now() - startTime);
      latencyEl.textContent = `Status: ${response.status} ${response.statusText} (${elapsed}ms)`;

      const resText = await response.text();
      try {
        const parsed = JSON.parse(resText);
        resEl.textContent = JSON.stringify(parsed, null, 2);
      } catch (e) {
        resEl.textContent = resText;
      }
    } catch (err) {
      const elapsed = Math.round(performance.now() - startTime);
      latencyEl.textContent = `Error (${elapsed}ms)`;
      resEl.textContent = 'Lỗi thực thi API: ' + err.message;
    }
  });

  // 10. Quản Trị Viên & Phân Quyền (RBAC)
  const roleNameMap = {
    super_admin: 'Super Admin',
    admin: 'Administrator',
    moderator: 'Moderator',
    operator: 'Operator'
  };

  const loadAdmins = async () => {
    try {
      const res = await apiFetch('/api/v1/admin/users');
      const json = await res.json();
      if (json.code !== 0) {
        showToast(json.message || 'Lỗi tải danh sách admin', 'error');
        return;
      }

      const admins = json.data || [];
      const totalAdmins = admins.length;
      const superAdmins = admins.filter(a => a.role === 'super_admin').length;
      const activeAdmins = admins.filter(a => a.is_active).length;

      document.getElementById('kpi-admin-total').textContent = totalAdmins;
      document.getElementById('kpi-admin-super').textContent = superAdmins;
      document.getElementById('kpi-admin-active').textContent = activeAdmins;
      document.getElementById('admin-count-badge').textContent = `${totalAdmins} Tài khoản`;

      const lastLoginAdmin = admins.find(a => a.last_login_at);
      if (lastLoginAdmin && lastLoginAdmin.last_login_at) {
        document.getElementById('kpi-admin-lastlogin').textContent = new Date(lastLoginAdmin.last_login_at).toLocaleTimeString();
      }

      const tbody = document.querySelector('#table-admins tbody');
      tbody.innerHTML = '';

      admins.forEach(u => {
        const roleClass = `badge-${u.role}`;
        const roleLabel = roleNameMap[u.role] || u.role;
        const statusClass = u.is_active ? 'badge-active' : 'badge-banned';
        const statusLabel = u.is_active ? 'Hoạt động' : 'Tạm khóa';
        const lastLoginStr = u.last_login_at ? new Date(u.last_login_at).toLocaleString() : 'Chưa đăng nhập';

        tbody.innerHTML += `
          <tr>
            <td>
              <div style="display:flex;align-items:center;gap:10px;">
                <img src="${u.avatar_url}" style="width:34px;height:34px;border-radius:50%;background:#1e293b;border:1px solid #334155;" onerror="this.src='https://cdn-icons-png.flaticon.com/512/3135/3135715.png'">
                <div>
                  <b style="color:#fff;">${u.display_name}</b>
                  <div style="font-size:11px;color:#38bdf8;font-family:monospace;">@${u.username}</div>
                </div>
              </div>
            </td>
            <td><a href="mailto:${u.email}" style="color:#94a3b8;font-size:12px;">${u.email}</a></td>
            <td><span class="${roleClass}">${roleLabel}</span></td>
            <td><span class="${statusClass}">${statusLabel}</span></td>
            <td style="font-size:11px;color:#94a3b8;">${lastLoginStr}</td>
            <td>
              <div style="display:flex;gap:5px;">
                <button class="btn-action-pass btn-change-pass" data-id="${u.id}" data-user="${u.username}" title="Đổi Mật Khẩu">
                  <i class="fa-solid fa-key"></i> Đổi MK
                </button>
                <button class="btn-action-edit btn-edit-user" data-id="${u.id}" data-user="${u.username}" data-name="${u.display_name}" data-email="${u.email}" data-role="${u.role}" data-status="${u.is_active}" title="Sửa">
                  <i class="fa-solid fa-pen"></i>
                </button>
                <button class="btn-action-del btn-del-user" data-id="${u.id}" data-user="${u.username}" title="Xóa">
                  <i class="fa-solid fa-trash"></i>
                </button>
              </div>
            </td>
          </tr>
        `;
      });

      // Bind Change Pass Button
      document.querySelectorAll('.btn-change-pass').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          const user = btn.getAttribute('data-user');
          document.getElementById('pass-target-id').value = id;
          document.getElementById('pass-target-user').value = `@${user}`;
          document.getElementById('pass-new').value = '';
          document.getElementById('pass-confirm').value = '';
          document.getElementById('modal-change-password').style.display = 'flex';
        });
      });

      // Bind Edit User Button
      document.querySelectorAll('.btn-edit-user').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = btn.getAttribute('data-id');
          const user = btn.getAttribute('data-user');
          const name = btn.getAttribute('data-name');
          const email = btn.getAttribute('data-email');
          const role = btn.getAttribute('data-role');
          const status = btn.getAttribute('data-status');

          document.getElementById('edit-target-id').value = id;
          document.getElementById('edit-admin-user').value = `@${user}`;
          document.getElementById('edit-admin-name').value = name;
          document.getElementById('edit-admin-email').value = email;
          document.getElementById('edit-admin-role').value = role;
          document.getElementById('edit-admin-status').value = status;
          document.getElementById('modal-edit-admin').style.display = 'flex';
        });
      });

      // Bind Delete User Button
      document.querySelectorAll('.btn-del-user').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          const user = btn.getAttribute('data-user');
          if (confirm(`Bạn có chắc chắn muốn xóa tài khoản quản trị viên [@${user}] không?`)) {
            const res = await apiFetch(`/api/v1/admin/users/${id}`, { method: 'DELETE' });
            const json = await res.json();
            if (json.code === 0) {
              showToast(json.message || `Đã xóa quản trị viên [@${user}]`, 'success');
              loadAdmins();
            } else {
              showToast(json.message || 'Lỗi khi xóa tài khoản', 'error');
            }
          }
        });
      });

    } catch (e) {
      console.error('Error loading admins:', e);
      showToast('Lỗi nạp danh sách quản trị viên: ' + e.message, 'error');
    }
  };

  // Form Tạo Admin Mới
  document.getElementById('form-create-admin')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('new-admin-user').value.trim();
    const password = document.getElementById('new-admin-pass').value.trim();
    const display_name = document.getElementById('new-admin-name').value.trim();
    const email = document.getElementById('new-admin-email').value.trim();
    const role = document.getElementById('new-admin-role').value;
    const avatar_url = document.getElementById('new-admin-avatar').value.trim();

    const res = await apiFetch('/api/v1/admin/users', {
      method: 'POST',
      body: JSON.stringify({ username, password, display_name, email, role, avatar_url })
    });
    const json = await res.json();
    if (json.code === 0) {
      showToast(`Đã tạo tài khoản quản trị viên [@${username}] thành công!`, 'success');
      document.getElementById('new-admin-user').value = '';
      document.getElementById('new-admin-pass').value = '';
      document.getElementById('new-admin-name').value = '';
      document.getElementById('new-admin-email').value = '';
      loadAdmins();
    } else {
      showToast(json.message || 'Lỗi khi tạo quản trị viên', 'error');
    }
  });

  // Form Đổi Mật Khẩu Modal
  document.getElementById('form-change-password')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const targetId = document.getElementById('pass-target-id').value;
    const newPass = document.getElementById('pass-new').value;
    const confirmPass = document.getElementById('pass-confirm').value;

    if (newPass !== confirmPass) {
      showToast('Mật khẩu xác nhận không khớp, vui lòng nhập lại', 'warning');
      return;
    }

    if (newPass.length < 6) {
      showToast('Mật khẩu mới phải có ít nhất 6 ký tự', 'warning');
      return;
    }

    const res = await apiFetch(`/api/v1/admin/users/${targetId}/password`, {
      method: 'PUT',
      body: JSON.stringify({ new_password: newPass })
    });
    const json = await res.json();
    if (json.code === 0) {
      showToast(json.message || 'Đã đổi mật khẩu thành công!', 'success');
      document.getElementById('modal-change-password').style.display = 'none';
    } else {
      showToast(json.message || 'Lỗi khi đổi mật khẩu', 'error');
    }
  });

  // Form Chỉnh Sửa Admin Modal
  document.getElementById('form-edit-admin')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const targetId = document.getElementById('edit-target-id').value;
    const display_name = document.getElementById('edit-admin-name').value.trim();
    const email = document.getElementById('edit-admin-email').value.trim();
    const role = document.getElementById('edit-admin-role').value;
    const is_active = document.getElementById('edit-admin-status').value === 'true';

    const res = await apiFetch(`/api/v1/admin/users/${targetId}`, {
      method: 'PUT',
      body: JSON.stringify({ display_name, email, role, is_active })
    });
    const json = await res.json();
    if (json.code === 0) {
      showToast('Đã cập nhật thông tin quản trị viên thành công!', 'success');
      document.getElementById('modal-edit-admin').style.display = 'none';
      loadAdmins();
    } else {
      showToast(json.message || 'Lỗi khi cập nhật quản trị viên', 'error');
    }
  });

  // Close Modals
  document.getElementById('btn-close-modal-pass')?.addEventListener('click', () => {
    document.getElementById('modal-change-password').style.display = 'none';
  });
  document.getElementById('btn-cancel-pass')?.addEventListener('click', () => {
    document.getElementById('modal-change-password').style.display = 'none';
  });
  document.getElementById('btn-close-modal-edit')?.addEventListener('click', () => {
    document.getElementById('modal-edit-admin').style.display = 'none';
  });
  document.getElementById('btn-cancel-edit')?.addEventListener('click', () => {
    document.getElementById('modal-edit-admin').style.display = 'none';
  });

  // Quick Change Password for Currently Logged-in User
  document.getElementById('btn-quick-change-pass')?.addEventListener('click', () => {
    const curUser = localStorage.getItem('shareit_admin_username') || 'admin';
    const curId = localStorage.getItem('shareit_admin_id') || 'admin_01';
    document.getElementById('pass-target-id').value = curId;
    document.getElementById('pass-target-user').value = `@${curUser} (Tài khoản hiện tại)`;
    document.getElementById('pass-new').value = '';
    document.getElementById('pass-confirm').value = '';
    document.getElementById('modal-change-password').style.display = 'flex';
  });

  document.getElementById('sidebar-user-info')?.addEventListener('click', () => {
    document.querySelector('.nav-item[data-tab="admins"]')?.click();
  });

  // Refresh Button
  document.getElementById('btn-refresh')?.addEventListener('click', () => {
    const activeTab = document.querySelector('.nav-item.active')?.getAttribute('data-tab') || 'dashboard';
    if (activeTab === 'dashboard') loadDashboard();
    if (activeTab === 'devices') loadDevices();
    if (activeTab === 'mcds') loadMCDS();
    if (activeTab === 'family') loadFamily();
    if (activeTab === 'ads') loadAds();
    if (activeTab === 'plans') loadPlans();
    if (activeTab === 'coin') loadCoin();
    if (activeTab === 'admins') loadAdmins();
    if (activeTab === 'logs') loadLogs();
    showToast('Đã làm mới dữ liệu trang quản trị', 'info');
  });

  // Init
  checkAuth();
});
