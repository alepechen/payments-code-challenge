import React from 'react'
import {
  FlexRow,
  StatusBadge,
  Table,
  TableBodyWrapper,
  TableHeaderRow,
  TableCell,
  TableHeader,
  TableHeaderWrapper,
  TableRow,
} from '../components/components'
import { I18N } from '../constants/i18n'
import type { Payment } from '../types/payment'

export type Column<T> = {
  [K in keyof T]: {
    key: K
    header: string
    render?: (value: T[K]) => React.ReactNode
  }
}[keyof T]

const columns: Column<Payment>[] = [
  {
    key: 'id',
    header: I18N.TABLE_HEADER_PAYMENT_ID,
  },
  {
    key: 'date',
    header: I18N.TABLE_HEADER_DATE,
    render: (value) =>
      new Date(value).toLocaleString('en-GB', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }),
  },
  {
    key: 'amount',
    header: I18N.TABLE_HEADER_AMOUNT,
    render: (value) => Number(value).toFixed(2),
  },
  {
    key: 'customerName',
    header: I18N.TABLE_HEADER_CUSTOMER,
    render: (value) => value ?? I18N.EMPTY_CUSTOMER,
  },
  {
    key: 'currency',
    header: I18N.TABLE_HEADER_CURRENCY,
    render: (value) => value ?? I18N.EMPTY_CURRENCY,
  },
  {
    key: 'status',
    header: I18N.TABLE_HEADER_STATUS,
    render: (value) => <StatusBadge status={value}>{value}</StatusBadge>,
  },
]

export const PaymentTable = ({ data }: { data: Payment[] }) => {
  return (
    <FlexRow>
      <Table>
        <TableHeaderWrapper>
          <TableHeaderRow>
            {columns.map((col) => (
              <TableHeader key={String(col.key)}>{col.header}</TableHeader>
            ))}
          </TableHeaderRow>
        </TableHeaderWrapper>

        <TableBodyWrapper>
          {data.map((row: Payment, rowIndex: number) => (
            <TableRow key={rowIndex}>
              {columns.map((col) => (
                <TableCell key={String(col.key)}>
                  {renderCell(col, row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBodyWrapper>
      </Table>
    </FlexRow>
  )
}

function renderCell<T>(col: Column<T>, row: T): React.ReactNode {
  const value = row[col.key]
  return col.render ? col.render(value) : String(value ?? '')
}
