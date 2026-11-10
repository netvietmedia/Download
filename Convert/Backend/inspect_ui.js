const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const screenshotDir = path.join(__dirname, 'screenshots');
  if (!fs.existsSync(screenshotDir)) fs.mkdirSync(screenshotDir, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  console.log('Navigating to http://localhost:6030/admin...');
  await page.goto('http://localhost:6030/admin', { waitUntil: 'networkidle' });
  const userField = page.locator('#login-user');
  if (await userField.isVisible()) {
    console.log('Logging in with admin / admin@123456...');
    await page.fill('#login-user', 'admin');
    await page.fill('#login-pass', 'admin@123456');
    await page.click('#login-form button[type=submit]');
    await page.waitForTimeout(1500);
  }
  await page.screenshot({ path: path.join(screenshotDir, '01_dashboard.png'), fullPage: true });
  console.log('Saved 01_dashboard.png');
  const tabs = ['devices', 'mcds', 'family', 'ads', 'plans', 'coin', 'admins', 'logs', 'tester'];
  for (const t of tabs) {
    const el = page.locator('button.nav-item[data-tab="' + t + '"]');
    if (await el.count() > 0) {
      await el.click();
      await page.waitForTimeout(800);
      await page.screenshot({ path: path.join(screenshotDir, 'tab_' + t + '.png'), fullPage: true });
      console.log('Saved tab_' + t + '.png');
      if (t === 'coin') {
        const subtabs = ['tasks', 'rewards', 'redemptions', 'adjust', 'ledger'];
        for (const st of subtabs) {
          const stEl = page.locator('button.coin-tab-btn[data-sub="' + st + '"]');
          if (await stEl.count() > 0) {
            await stEl.click();
            await page.waitForTimeout(500);
            await page.screenshot({ path: path.join(screenshotDir, 'tab_coin_' + st + '.png'), fullPage: true });
            console.log('Saved tab_coin_' + st + '.png');
          }
        }
      }
      if (t === 'admins') {
        // Test Open Change Password Modal
        const passBtn = page.locator('.btn-change-pass').first();
        if (await passBtn.count() > 0) {
          await passBtn.click();
          await page.waitForTimeout(400);
          await page.screenshot({ path: path.join(screenshotDir, 'modal_change_password.png'), fullPage: true });
          console.log('Saved modal_change_password.png');
          await page.click('#btn-close-modal-pass');
          await page.waitForTimeout(300);
        }

        // Test Open Edit Admin Modal
        const editBtn = page.locator('.btn-edit-user').first();
        if (await editBtn.count() > 0) {
          await editBtn.click();
          await page.waitForTimeout(400);
          await page.screenshot({ path: path.join(screenshotDir, 'modal_edit_admin.png'), fullPage: true });
          console.log('Saved modal_edit_admin.png');
          await page.click('#btn-close-modal-edit');
          await page.waitForTimeout(300);
        }
      }
    }
  }
  console.log('All screenshots captured!');
  await browser.close();
})();