import { describe, expect, it } from 'vitest'
import { initialOf } from './VenuePage'

describe('bookable initial', () => {
  it('takes the word that tells siblings apart, ignoring notes in brackets', () => {
    expect(initialOf('Court A')).toBe('A')
    expect(initialOf('Group Room 1')).toBe('1')
    expect(initialOf('Court 1 (clay)')).toBe('1')
    expect(initialOf('Dr. Rao')).toBe('R')
  })
})
