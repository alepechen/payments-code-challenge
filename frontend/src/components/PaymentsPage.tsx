import { Container, ErrorBox } from './components'
import { PaymentTable } from './PaymentTable'
import { SearchFilters } from './SearchFilters'
import { Pagination } from './Pagination'
import { usePayments } from '../hooks/usePayment'
import { PaymentSearchQuery } from '../types/payment'

const initialQuery: PaymentSearchQuery = {
  filters: {
    search: '',
    currency: '',
  },
  page: 1,
  pageSize: 5,
}

export const PaymentsPage = () => {
  const { payments, error, total, query, setQuery, refetch } = usePayments()

  const handleSearch = () => {
    const newQuery = {
      ...query,
      page: 1,
    }
    setQuery(newQuery)
    refetch(newQuery)
  }
  const handleClear = () => {
    setQuery(initialQuery)
    refetch(initialQuery)
  }

  const onClickNext = () => {
    const newQuery = {
      ...query,
      page: query.page !== total ? query.page + 1 : query.page,
    }
    setQuery(newQuery)
    refetch(newQuery)
  }

  const onClickPrevious = () => {
    const newQuery = {
      ...query,
      page: query.page !== 1 ? query.page - 1 : query.page,
    }
    setQuery(newQuery)
    refetch(newQuery)
  }
  return (
    <Container>
      <Container>
        <SearchFilters
          query={query}
          updateQuery={setQuery}
          handleSearch={handleSearch}
          handleClear={handleClear}
        />
        {error && <ErrorBox>{error}</ErrorBox>}
        <PaymentTable data={payments} />
        <Pagination
          page={query.page}
          total={total}
          onClickNext={onClickNext}
          onClickPrevious={onClickPrevious}
        />
      </Container>
    </Container>
  )
}
