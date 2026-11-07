import { Toaster as Sonner } from 'sonner'
import { useTheme } from '@/lib/theme'

// Toasts (sonner) themed with our tokens; sonner announces them in a live region (§7).
export function Toaster() {
  const { resolved } = useTheme()
  return (
    <Sonner
      theme={resolved}
      toastOptions={{
        classNames: {
          toast: 'bg-popover text-popover-foreground border border-border shadow-lg rounded-md',
          description: 'text-muted-foreground',
        },
      }}
    />
  )
}
