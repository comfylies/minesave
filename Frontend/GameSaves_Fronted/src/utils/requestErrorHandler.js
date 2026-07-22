/**
 * Builds the Axios response-error branch from injected side effects so its
 * cancellation, authentication, and notification behavior remains testable.
 */
export function createResponseErrorHandler({ isCancellation, onUnauthorized, showError }) {
  return error => {
    if (isCancellation(error)) {
      return Promise.reject(error)
    }

    if (error.response?.status === 401) {
      onUnauthorized()
      return Promise.reject(error)
    }

    const message = error.response?.data?.message || error.message || '网络错误'
    showError(message)
    return Promise.reject(error)
  }
}
