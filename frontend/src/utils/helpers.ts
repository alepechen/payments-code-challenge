import { I18N } from '../constants/i18n'
import { PaymentSearchQuery } from '../types/payment'

export function mapApiError(status?: number): string {
  switch (status) {
    case 404:
      return I18N.PAYMENT_NOT_FOUND
    case 500:
      return I18N.INTERNAL_SERVER_ERROR
    default:
      return I18N.SOMETHING_WENT_WRONG
  }
}

export const buildSearchParams = (query: PaymentSearchQuery): string => {
  const params = new URLSearchParams()

  if (query.filters) {
    Object.entries(query.filters).forEach(([key, value]) => {
      if (value) params.append(key, value)
    })
  }
  if (query.page !== undefined) params.append('page', query.page.toString())
  if (query.pageSize !== undefined)
    params.append('pageSize', query.pageSize.toString())

  return params.toString()
}
