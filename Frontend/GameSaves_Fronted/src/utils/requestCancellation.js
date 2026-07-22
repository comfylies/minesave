/**
 * Identifies expected request cancellations without coupling callers to Axios.
 * The optional predicate keeps Axios' native cancellation detection available.
 */
export function isRequestCancellation(error, isAxiosCancel = () => false) {
  return Boolean(
    isAxiosCancel(error) ||
    error?.code === 'ERR_CANCELED' ||
    error?.name === 'CanceledError'
  )
}
