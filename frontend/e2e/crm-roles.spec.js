import { expect, test } from '@playwright/test';
import { MANAGER_BASE_URL, MANAGER_EMAIL, MANAGER_PASSWORD } from './credentials.js';
import { createCompletedOrderForCustomer } from './fixtures.js';

async function signInManager(page) {
  await page.goto(`${MANAGER_BASE_URL}/manager/login`);
  await page.getByLabel('EMAIL').fill(MANAGER_EMAIL);
  await page.getByLabel('MẬT KHẨU').fill(MANAGER_PASSWORD);
  const loginResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/manager/auth/login' && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: /ĐĂNG NHẬP NGƯỜI QUẢN LÝ/i }).click();
  expect((await loginResponse).status()).toBe(200);
}

test('Manager and customer complete the CRM customer, feedback, and survey workflow', async ({ browser, page }) => {
  test.setTimeout(90_000);
  const runId = `${test.info().project.name}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const email = `customer-${runId}@crm.test`;
  const fullName = `Khách CRM E2E ${runId}`;
  const password = 'Customer123';
  const preference = `e2e phong cách ${runId}`.toLowerCase();
  const createdCustomerEmail = `manager-added-${runId}@crm.test`;
  const createdCustomerName = `Khách được thêm ${runId}`;
  const surveyTitle = `E2E khảo sát sản phẩm mới ${runId}`;
  const surveyAnswer = 'Áo khoác tối giản';
  const feedbackComment = `e2e feedback ${runId}`;
  const orderCode = (BigInt(Date.now()) * 1_000_000n + BigInt(Math.floor(Math.random() * 1_000_000))).toString();
  let managerPage = await browser.newPage();

  try {
    await page.goto('/dang-ky');
    await page.getByLabel('HỌ VÀ TÊN').fill(fullName);
    await page.getByLabel('SỐ ĐIỆN THOẠI').fill('0900000000');
    await page.getByLabel('EMAIL').fill(email);
    await page.getByLabel('MẬT KHẨU').fill(password);

    const registerResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/auth/register' && response.request().method() === 'POST',
    );
    const loginResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'TẠO TÀI KHOẢN →' }).click();
    expect((await registerResponse).status()).toBe(201);
    expect((await loginResponse).status()).toBe(200);

    await page.goto('/tai-khoan');
    await page.getByLabel('TUỔI').fill('24');
    await page.getByLabel('PHONG CÁCH ƯA THÍCH').fill(preference);
    const profileSaveResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/customers/me' && response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: 'LƯU THAY ĐỔI' }).click();
    const savedProfileResponse = await profileSaveResponse;
    expect(savedProfileResponse.status()).toBe(200);
    expect(await savedProfileResponse.json()).toMatchObject({ age: 24, preferences: preference });

    await page.goto('/tai-khoan');
    await expect(page.getByLabel('TUỔI')).toHaveValue('24');
    await expect(page.getByLabel('PHONG CÁCH ƯA THÍCH')).toHaveValue(preference);
    await page.getByRole('button', { name: 'Đăng xuất khỏi tài khoản' }).click();
    await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByLabel('EMAIL').fill(email);
    await page.getByLabel('MẬT KHẨU').fill(password);
    const customerLoginResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'ĐĂNG NHẬP →' }).click();
    expect((await customerLoginResponse).status()).toBe(200);
    await page.goto('/tai-khoan');
    await expect(page.getByLabel('TUỔI')).toHaveValue('24');
    await expect(page.getByLabel('PHONG CÁCH ƯA THÍCH')).toHaveValue(preference);

    const order = await createCompletedOrderForCustomer(email, orderCode);

    await signInManager(managerPage);

    await managerPage.getByRole('button', { name: 'Khách hàng', exact: true }).click();
    await managerPage.getByRole('button', { name: 'Thêm khách hàng' }).click();
    await managerPage.getByLabel('Họ tên').fill(createdCustomerName);
    await managerPage.getByLabel('Email').fill(createdCustomerEmail);
    await managerPage.getByLabel('Mật khẩu ban đầu').fill('NewCustomer123');
    await managerPage.getByRole('button', { name: 'Lưu khách hàng' }).click();

    const createdCustomerRow = managerPage.getByRole('row').filter({ hasText: createdCustomerEmail });
    await expect(createdCustomerRow).toBeVisible();
    await createdCustomerRow.getByRole('button', { name: 'Khóa', exact: true }).click();
    await managerPage.getByLabel('Lý do khóa gửi đến người dùng').fill('Kiểm tra thao tác khóa tài khoản E2E.');
    await managerPage.getByRole('button', { name: 'Xác nhận khóa' }).click();
    await expect(createdCustomerRow.getByText('Đã khóa')).toBeVisible();

    await createdCustomerRow.getByRole('button', { name: 'Xóa', exact: true }).click();
    await managerPage.getByRole('button', { name: 'Xóa tài khoản' }).click();
    await expect(createdCustomerRow).toHaveCount(0);

    const customerReportResponse = managerPage.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/manager/reports/customers' && response.request().method() === 'GET',
    );
    await managerPage.getByRole('button', { name: 'Báo cáo', exact: true }).click();
    const customerReport = await customerReportResponse;
    expect(customerReport.status()).toBe(200);
    const customerReportData = await customerReport.json();
    const ageGroup = customerReportData.ages.find((item) => item.label === '18–24');
    const preferenceGroup = customerReportData.preferences.find((item) => item.label === preference);
    expect(ageGroup?.count).toBeGreaterThan(0);
    expect(ageGroup?.percent).toBeGreaterThan(0);
    expect(preferenceGroup?.count).toBeGreaterThan(0);
    expect(preferenceGroup?.percent).toBeGreaterThan(0);
    await expect(managerPage.getByRole('heading', { name: 'Phân bố độ tuổi' })).toBeVisible();
    await expect(managerPage.getByRole('heading', { name: 'Sở thích đã khai báo' })).toBeVisible();
    await expect(managerPage.getByText('18–24', { exact: true })).toBeVisible();
    await expect(managerPage.getByText(preference, { exact: true })).toBeVisible();

    await managerPage.getByRole('button', { name: 'Khảo sát', exact: true }).click();
    await managerPage.getByRole('button', { name: 'Tạo khảo sát' }).click();
    await managerPage.getByLabel('Tên khảo sát').fill(surveyTitle);
    await managerPage.getByLabel('Nội dung câu hỏi 1').fill('Bạn quan tâm kiểu dáng nào cho sản phẩm mới?');
    await managerPage.getByLabel('Câu 1, phương án 1').fill(surveyAnswer);
    await managerPage.getByLabel('Câu 1, phương án 2').fill('Áo thun cơ bản');
    await managerPage.getByRole('button', { name: 'Lưu bản nháp' }).click();

    const surveyRow = managerPage.getByRole('row').filter({ hasText: surveyTitle });
    await expect(surveyRow).toBeVisible();
    await surveyRow.getByRole('button', { name: 'Phát hành' }).click();
    await expect(surveyRow.getByText('Đã phát hành')).toBeVisible();

    const customerSurveysResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/surveys/mine' && response.request().method() === 'GET',
    );
    await page.goto('/khao-sat');
    const customerSurveys = await customerSurveysResponse;
    expect(customerSurveys.status()).toBe(200);
    expect((await customerSurveys.json()).map((survey) => survey.title)).toContain(surveyTitle);
    const surveyCard = page.getByRole('button').filter({ hasText: surveyTitle });
    await expect(surveyCard).toBeVisible({ timeout: 15_000 });
    await surveyCard.click();
    await page.getByRole('radio', { name: surveyAnswer }).check();
    const surveyResponse = page.waitForResponse((response) =>
      /^\/api\/surveys\/\d+\/responses$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: /GỬI CÂU TRẢ LỜI/ }).click();
    expect((await surveyResponse).status()).toBe(201);
    await expect(page.getByText('Cảm ơn bạn đã chia sẻ gu thời trang!')).toBeVisible();

    await managerPage.getByRole('button', { name: 'Báo cáo', exact: true }).click();
    await managerPage.getByRole('button', { name: 'Kết quả khảo sát' }).click();
    const surveySelect = managerPage.getByLabel('Chọn khảo sát');
    const surveyOption = surveySelect.locator('option').filter({ hasText: surveyTitle });
    await expect(surveyOption).toHaveCount(1);
    await expect(surveyOption).toContainText('1 lượt');
    const surveyId = await surveyOption.getAttribute('value');
    const resultsResponse = managerPage.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/manager/reports/surveys/${surveyId}/results` && response.request().method() === 'GET',
    );
    await surveySelect.selectOption(await surveyOption.getAttribute('value'));
    const surveyResults = await resultsResponse;
    expect(surveyResults.status()).toBe(200);
    expect((await surveyResults.json()).responses).toBe(1);
    await expect(managerPage.getByText(/1 lượt trả lời/)).toBeVisible();
    await expect(managerPage.getByText(surveyAnswer, { exact: true })).toBeVisible();

    await managerPage.close();
    managerPage = await browser.newPage();
    await page.goto('/don-hang');
    await expect(page.getByRole('heading', { name: 'Đơn hàng của tôi' })).toBeVisible();
    const orderReviewButton = page.getByRole('button', { name: 'Đánh giá', exact: true });
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        await orderReviewButton.waitFor({ state: 'visible', timeout: 7_000 });
        break;
      } catch (error) {
        const retryButton = page.getByRole('button', { name: 'Thử lại', exact: true });
        if (attempt === 2 || !(await retryButton.isVisible().catch(() => false))) throw error;
        await retryButton.click();
      }
    }
    await orderReviewButton.click();
    await page.getByRole('button', { name: `Đánh giá E2E CRM Feedback Product` }).click();
    await page.getByRole('button', { name: '4 sao — Hài lòng' }).click();
    await page.getByLabel('Bình luận (tùy chọn)').fill(feedbackComment);
    await page.getByRole('button', { name: 'Gửi đánh giá' }).click();
    await expect(page.getByText('Đã gửi đánh giá thành công.')).toBeVisible();

    await signInManager(managerPage);
    await managerPage.getByRole('button', { name: 'Phản hồi', exact: true }).click();
    const feedbackRow = managerPage.getByRole('row').filter({ hasText: feedbackComment });
    const feedbackRetry = managerPage.getByRole('button', { name: 'Thử lại', exact: true });
    await expect.poll(async () => {
      if (await feedbackRow.isVisible().catch(() => false)) return 'loaded';
      if (await feedbackRetry.isVisible().catch(() => false)) return 'retry';
      return 'loading';
    }, { timeout: 15_000 }).not.toBe('loading');
    if (await feedbackRetry.isVisible().catch(() => false)) {
      const feedbackResponse = managerPage.waitForResponse((response) =>
        new URL(response.url()).pathname === '/api/manager/feedback' && response.request().method() === 'GET',
      );
      await feedbackRetry.click();
      const response = await feedbackResponse;
      expect(response.status()).toBe(200);
      expect((await response.json()).content.map((item) => item.comment)).toContain(feedbackComment);
    }
    await expect(feedbackRow).toBeVisible();
    const feedbackStatus = feedbackRow.getByLabel(/^Trạng thái phản hồi /);
    await feedbackStatus.selectOption('IN_PROGRESS');
    await feedbackRow.getByRole('button', { name: 'Trả lời' }).click();
    await managerPage.getByLabel('Nội dung trả lời').fill('Nova Shop đã tiếp nhận phản hồi của bạn.');
    await managerPage.getByRole('button', { name: 'Lưu trả lời' }).click();
    await expect(feedbackRow.getByText('Nova Shop đã tiếp nhận phản hồi của bạn.')).toBeVisible();

    expect(order.orderId).toBeTruthy();
  } finally {
    if (!managerPage.isClosed()) await managerPage.close();
  }
});
