import type { InputHTMLAttributes } from 'react'

type MoneyInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> & {
  /** Monto como texto sin separadores, por ejemplo "20000". */
  value: string
  /** Recibe el monto solo con dígitos, por ejemplo "20000". */
  onChange: (value: string) => void
}

/**
 * Campo de monto en pesos chilenos.
 * Se muestra con separador de miles ("20.000") pero entrega solo dígitos,
 * evitando que "20.000" se interprete como 20.
 */
export default function MoneyInput({ value, onChange, ...props }: MoneyInputProps) {
  const display = value === '' ? '' : Number(value).toLocaleString('es-CL')

  return (
    <input
      {...props}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={display}
      onChange={(event) => onChange(event.target.value.replace(/\D/g, ''))}
    />
  )
}
