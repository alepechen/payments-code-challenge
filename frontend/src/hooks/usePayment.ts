import { useEffect, useState, useTransition } from 'react'
import {
  PaymentSearchQuery,
  PaymentSearchResponse,
  FetchError,
} from '../types/payment'
import { mapApiError } from '../utils/helpers'
import { fetchData } from '../api/api'

interface PaymentData extends PaymentSearchResponse {
  error: string | null
  refetch: (query: PaymentSearchQuery) => void
  query: PaymentSearchQuery
  setQuery: (query: PaymentSearchQuery) => void
}
const initialQuery: PaymentSearchQuery = {
  filters: {
    search: '',
    currency: '',
  },
  page: 1,
  pageSize: 5,
}
export const usePayments = (): PaymentData => {
  const [data, setData] = useState<PaymentSearchResponse>({
    payments: [],
    total: 0,
    page: 0,
    pageSize: 0,
  })
  const [error, setError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()
  const [query, setQuery] = useState<PaymentSearchQuery>(initialQuery)
  //const updateQuery = (newQuery: PaymentSearchQuery) => setQuery(newQuery)
  const fetchPayments = (query: PaymentSearchQuery) => {
    startTransition(async () => {
      try {
        setError(null)
        const result = await fetchData(query)
        setData({
          payments: result.payments ?? [],
          total: result.total ?? 0,
          page: result.page,
          pageSize: result.pageSize,
        })
      } catch (err: unknown) {
        const fetchErr = err as FetchError
        setError(mapApiError(fetchErr?.status))
      }
    })
  }
  useEffect(() => {
    fetchPayments(initialQuery)
  }, [])

  return {
    ...data,
    query,
    setQuery,
    error,
    refetch: fetchPayments,
  }
}
