import React, { useCallback } from 'react'
import { FilterRow, SearchInput, SearchButton, ClearButton } from './components'
import { I18N } from '../constants/i18n'
import { Filters, PaymentSearchQuery } from '../types/payment'
import { CURRENCIES as currencyOptions } from '../constants'
import { SelectComponent } from './Select'

type ButtonHandler = React.MouseEventHandler<HTMLButtonElement>

interface SearchFiltersProps {
  query: PaymentSearchQuery
  updateQuery: (query: PaymentSearchQuery) => void
  handleSearch: ButtonHandler
  handleClear: ButtonHandler
}

export const SearchFilters = ({
  query,
  updateQuery,
  handleSearch,
  handleClear,
}: SearchFiltersProps) => {
  const { filters } = query
  const isFilters = Object.values(filters).some(Boolean)
  const updateFilter = useCallback(
    (patch: Partial<typeof filters>) =>
      updateQuery({ ...query, filters: { ...filters, ...patch } }),
    [query, filters, updateQuery],
  )
  return (
    <FilterRow>
      <SearchInput
        aria-label={I18N.SEARCH_LABEL}
        placeholder={I18N.SEARCH_PLACEHOLDER}
        value={filters.search}
        onChange={(e) => updateFilter({ search: e.target.value })}
      />
      <SelectComponent
        ariaLabel={I18N.CURRENCY_FILTER_LABEL}
        placeholder={I18N.CURRENCIES_OPTION}
        options={currencyOptions}
        value={filters.currency ?? ''}
        onChange={(value) => updateFilter({ currency: value })}
      />
      <SearchButton aria-label={I18N.SEARCH_BUTTON} onClick={handleSearch}>
        {I18N.SEARCH_BUTTON}
      </SearchButton>{' '}
      {isFilters && (
        <ClearButton aria-label={I18N.CLEAR_FILTERS} onClick={handleClear}>
          {I18N.CLEAR_FILTERS}
        </ClearButton>
      )}
    </FilterRow>
  )
}
