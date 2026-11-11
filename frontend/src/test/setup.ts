import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => cleanup())

// jsdom doesn't lay pages out, so it has no scrollIntoView; the combobox calls it to keep the
// highlighted option visible. A no-op is enough for tests.
Element.prototype.scrollIntoView = function scrollIntoView() {}
