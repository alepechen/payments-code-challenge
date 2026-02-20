import { Select } from './components'

type SelectProps<T extends string> = {
  options: T[]
  value: T | ''
  onChange: (value: T | '') => void
  ariaLabel?: string
  placeholder?: string
}

export const SelectComponent = <T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
  placeholder,
}: SelectProps<T>) => {
  return (
    <Select
      value={value}
      aria-label={ariaLabel}
      onChange={(e) => onChange(e.target.value as T | '')}
    >
      {placeholder && <option value=''>{placeholder}</option>}
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </Select>
  )
}
