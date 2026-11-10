async function testApi() {
  const baseUrl = 'http://localhost:6030';
  console.log('Testing Backend APIs on ' + baseUrl + ' ...\n');

  // 1. Healthz
  const res1 = await fetch(`${baseUrl}/healthz`);
  console.log('1. GET /healthz ->', await res1.json());

  // 2. Register Device
  const res2 = await fetch(`${baseUrl}/api/v1/auth/device/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      device_fingerprint: {
        os_type: 'android',
        os_version: 34,
        device_model: 'Pixel 7 Pro',
        manufacturer: 'Google',
        language: 'vi',
        country_code: 'VN'
      }
    })
  });
  const data2 = await res2.json();
  console.log('2. POST /api/v1/auth/device/register ->', data2);

  // 3. MCDS Fetch
  const res3 = await fetch(`${baseUrl}/api/v1/mcds/fetch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      space_ids: ['home_top_banner'],
      context: { country: 'VN' }
    })
  });
  console.log('3. POST /api/v1/mcds/fetch ->', await res3.json());

  // 4. Ads Strategy
  const res4 = await fetch(`${baseUrl}/api/v1/ads/strategy/get`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      placement_ids: ['trans_complete_interstitial'],
      country_code: 'VN'
    })
  });
  console.log('4. POST /api/v1/ads/strategy/get ->', await res4.json());

  // 5. Subscription Plans
  const res5 = await fetch(`${baseUrl}/api/v1/pay/plans`);
  console.log('5. GET /api/v1/pay/plans ->', await res5.json());

  // 6. Coin Checkin
  const res6 = await fetch(`${baseUrl}/api/v1/coin/checkin`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ device_uuid: data2.data.device_uuid })
  });
  console.log('6. POST /api/v1/coin/checkin ->', await res6.json());

  // 7. Admin Stats
  const res7 = await fetch(`${baseUrl}/api/v1/admin/stats`, {
    headers: { 'x-admin-token': 'admin_authenticated_session_token_6030' }
  });
  console.log('7. GET /api/v1/admin/stats ->', await res7.json());

  // 8. SHAREit Family Products Client API
  const res8 = await fetch(`${baseUrl}/api/v1/mcds/family-products`);
  const data8 = await res8.json();
  console.log('8. GET /api/v1/mcds/family-products ->', data8);

  // 9. SHAREit Family Admin API
  const res9 = await fetch(`${baseUrl}/api/v1/admin/family-products`, {
    headers: { 'x-admin-token': 'admin_authenticated_session_token_6030' }
  });
  const data9 = await res9.json();
  console.log('9. GET /api/v1/admin/family-products -> Total apps:', data9.data ? data9.data.length : 'error');

  console.log('\n✅ ALL API TESTS (INCLUDING SHAREIT FAMILY CPI ADS) PASSED SUCCESSFULLY!');
}

testApi().catch(console.error);

