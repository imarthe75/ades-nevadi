import { Page, APIRequestContext } from '@playwright/test';

/**
 * ApiHelper — HTTP request/response intercepting and mocking
 * Handles: API mocking, request capture, response validation
 */
export class ApiHelper {
  private capturedRequests: Map<string, any[]> = new Map();

  constructor(
    private page: Page,
    private request?: APIRequestContext
  ) {}

  /**
   * Mock an API endpoint to return a specific response
   * @param method HTTP method (GET, POST, etc.)
   * @param urlPattern URL pattern to match (regex or string)
   * @param response Response body to return
   * @param status HTTP status code (default 200)
   */
  async mockApiEndpoint(
    method: string,
    urlPattern: string | RegExp,
    response: any,
    status: number = 200
  ): Promise<void> {
    await this.page.route(urlPattern, (route) => {
      if (route.request().method() === method) {
        route.fulfill({
          status,
          contentType: 'application/json',
          body: JSON.stringify(response),
        });
      } else {
        route.continue();
      }
    });
  }

  /**
   * Capture all requests to a specific endpoint
   * @param urlPattern URL pattern to capture
   * @param method HTTP method (default all)
   */
  async captureRequests(
    urlPattern: string | RegExp,
    method?: string
  ): Promise<void> {
    const key = urlPattern.toString();

    await this.page.route(urlPattern, (route) => {
      if (!method || route.request().method() === method) {
        const requests = this.capturedRequests.get(key) || [];
        requests.push({
          method: route.request().method(),
          url: route.request().url(),
          headers: route.request().headers(),
          postData: route.request().postData(),
          postDataJSON: route.request().postDataJSON().catch(() => null),
        });
        this.capturedRequests.set(key, requests);
      }
      route.continue();
    });
  }

  /**
   * Get captured requests
   * @param urlPattern URL pattern (must match captureRequests call)
   * @returns Array of captured request objects
   */
  getCapturedRequests(urlPattern: string | RegExp): any[] {
    return this.capturedRequests.get(urlPattern.toString()) || [];
  }

  /**
   * Clear captured requests
   */
  clearCapturedRequests(): void {
    this.capturedRequests.clear();
  }

  /**
   * Make a direct API request using context
   * Used when page is not available or for external API calls
   * @param method HTTP method
   * @param url URL to request
   * @param options Request options (headers, body, etc.)
   * @returns Response
   */
  async makeRequest(
    method: string,
    url: string,
    options: any = {}
  ): Promise<any> {
    if (!this.request) {
      throw new Error('APIRequestContext not available');
    }

    const requestOptions = {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    };

    let response;
    switch (method.toUpperCase()) {
      case 'GET':
        response = await this.request.get(url, requestOptions);
        break;
      case 'POST':
        response = await this.request.post(url, requestOptions);
        break;
      case 'PUT':
        response = await this.request.put(url, requestOptions);
        break;
      case 'PATCH':
        response = await this.request.patch(url, requestOptions);
        break;
      case 'DELETE':
        response = await this.request.delete(url, requestOptions);
        break;
      default:
        throw new Error(`Unsupported HTTP method: ${method}`);
    }

    return {
      status: response.status(),
      statusText: response.statusText(),
      headers: response.headers(),
      body: await response.json().catch(() => response.text()),
    };
  }

  /**
   * Simulate network error/offline
   * @param urlPattern URL pattern to intercept
   */
  async simulateNetworkError(urlPattern: string | RegExp): Promise<void> {
    await this.page.route(urlPattern, (route) => {
      route.abort('failed');
    });
  }

  /**
   * Simulate slow network (add delay to response)
   * @param urlPattern URL pattern to intercept
   * @param delayMs Delay in milliseconds
   */
  async simulateSlowNetwork(
    urlPattern: string | RegExp,
    delayMs: number = 2000
  ): Promise<void> {
    await this.page.route(urlPattern, async (route) => {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
      route.continue();
    });
  }

  /**
   * Simulate API error response
   * @param urlPattern URL pattern to intercept
   * @param status HTTP status code
   * @param errorMessage Error message
   */
  async simulateApiError(
    urlPattern: string | RegExp,
    status: number = 500,
    errorMessage: string = 'Internal Server Error'
  ): Promise<void> {
    await this.page.route(urlPattern, (route) => {
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ error: errorMessage }),
      });
    });
  }

  /**
   * Wait for API request to complete
   * @param urlPattern URL pattern to wait for
   * @param timeout Timeout in milliseconds
   */
  async waitForApiRequest(
    urlPattern: string | RegExp,
    timeout: number = 10000
  ): Promise<any> {
    return await this.page.waitForResponse(
      (response) => {
        if (urlPattern instanceof RegExp) {
          return urlPattern.test(response.url());
        }
        return response.url().includes(urlPattern as string);
      },
      { timeout }
    );
  }
}
