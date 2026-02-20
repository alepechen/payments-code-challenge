import { PaymentSearchResponse, PaymentSearchQuery } from '../types/payment'
import { buildSearchParams } from '../utils/helpers'

export const fetchData = async (
  query: PaymentSearchQuery,
): Promise<PaymentSearchResponse> => {
  const queryString = buildSearchParams(query)

  const response = await fetch(`/api/payments?${queryString}`)

  if (!response.ok) {
    throw { message: 'HTTP Error', status: response.status }
  }

  const result: PaymentSearchResponse = await response.json()

  return result
}
