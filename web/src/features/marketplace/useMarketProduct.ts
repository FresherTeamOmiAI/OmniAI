import { useEffect, useState } from 'react'
import type { Product } from '../../shared/types'
import { findProductBySku } from './loadProduct'

export function useMarketProduct(sku: string | undefined) {
  const [product, setProduct] = useState<Product | null>(null)
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    if (!sku) return
    let cancelled = false
    setProduct(null)
    setMissing(false)
    findProductBySku(sku)
      .then((found) => {
        if (cancelled) return
        if (found) setProduct(found)
        else setMissing(true)
      })
      .catch(() => {
        if (!cancelled) setMissing(true)
      })
    return () => {
      cancelled = true
    }
  }, [sku])

  return { product, missing }
}
