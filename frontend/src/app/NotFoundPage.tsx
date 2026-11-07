import { Link } from 'react-router'
import { EmptyState } from '@/components/patterns/States'

export function NotFoundPage() {
  return (
    <EmptyState title="This page doesn’t exist">
      <Link to="/" className="text-primary underline-offset-4 hover:underline">
        Go to Browse
      </Link>
    </EmptyState>
  )
}
