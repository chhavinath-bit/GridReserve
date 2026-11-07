import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type ThemePreference } from '@/lib/theme'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: 'system', label: 'System', Icon: Monitor },
  { value: 'light', label: 'Light', Icon: Sun },
  { value: 'dark', label: 'Dark', Icon: Moon },
]

export function ThemeToggle() {
  const { preference, setPreference } = useTheme()
  return (
    <Select value={preference} onValueChange={(value) => setPreference(value as ThemePreference)}>
      <SelectTrigger aria-label="Theme" className="h-8 w-28">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {OPTIONS.map(({ value, label, Icon }) => (
          <SelectItem key={value} value={value}>
            <span className="flex items-center gap-2">
              <Icon className="size-4" aria-hidden />
              {label}
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
