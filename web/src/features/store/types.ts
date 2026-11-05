// Khớp entity backend catalog/Product.java (xem docs/specs/data-model.md).
export interface Product {
  id: number
  sku: string
  name: string
  brand: string
  category: string
  skinTypes: string
  skinConcerns: string | null
  usageTime: 'am' | 'pm' | 'am,pm' | null
  description: string | null
  ingredientsInci: string
  sizeMl: number
  paoMonths: number
  priceWeb: number
  priceShopee: number
  priceTiktok: number
  stock: number
  active: boolean
  updatedAt: string
}
