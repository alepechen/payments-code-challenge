export interface Payment {
  id: string
  customerName: string
  amount: number
  customerAddress: string
  currency: string
  status: string
  date: string
  description: string
}

export interface PaymentSearchResponse {
  payments: Payment[]
  total: number
  page: number
  pageSize: number
}

export interface PaymentSearchQuery {
  filters: Filters
  page: number
  pageSize: number
}

export type Filters = {
  search?: string
  currency?: string
}

export type FetchError = {
  status: number
  message: string
}
