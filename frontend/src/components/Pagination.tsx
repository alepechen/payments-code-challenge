import { PaginationRow, PaginationButton } from './components'
import { I18N } from '../constants/i18n'

interface PaginationProps {
  page: number
  total: number
  onClickNext: () => void
  onClickPrevious: () => void
}

export const Pagination = ({
  page,
  total,
  onClickNext,
  onClickPrevious,
}: PaginationProps) => {
  return (
    <PaginationRow>
      <PaginationButton
        disabled={page === 1}
        aria-label={I18N.PREVIOUS_BUTTON}
        onClick={onClickPrevious}
      >
        {I18N.PREVIOUS_BUTTON}
      </PaginationButton>
      {`${I18N.PAGE_LABEL} ${page}`}
      <PaginationButton
        disabled={page === total}
        aria-label={I18N.NEXT_BUTTON}
        onClick={onClickNext}
      >
        {I18N.NEXT_BUTTON}
      </PaginationButton>
    </PaginationRow>
  )
}
