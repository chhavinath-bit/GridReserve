import { ChevronRight } from 'lucide-react'
import { Fragment } from 'react'
import { Link } from 'react-router'

export interface BreadcrumbItem {
  label: string
  to?: string
}

/** Where this page sits; the last item is the current page (WAI-ARIA breadcrumb pattern). */
export function Breadcrumb({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav aria-label="Breadcrumb" className="mb-3">
      <ol className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
        {items.map((item, index) => {
          const last = index === items.length - 1
          return (
            <Fragment key={`${item.label}-${index}`}>
              <li className="min-w-0 truncate">
                {item.to && !last ? (
                  <Link to={item.to} className="rounded-sm hover:text-foreground hover:underline underline-offset-4">
                    {item.label}
                  </Link>
                ) : (
                  <span aria-current={last ? 'page' : undefined} className={last ? 'text-foreground' : undefined}>
                    {item.label}
                  </span>
                )}
              </li>
              {last ? null : (
                <li aria-hidden>
                  <ChevronRight className="size-3.5" />
                </li>
              )}
            </Fragment>
          )
        })}
      </ol>
    </nav>
  )
}
