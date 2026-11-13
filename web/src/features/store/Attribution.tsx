// Dòng ghi nguồn bắt buộc theo giấy phép ODbL / CC BY-SA 3.0 của Open Beauty Facts
// (docs/catalog/du-lieu-san-pham-that.md). Hiện ở mọi trang có dữ liệu hoặc ảnh sản phẩm.
export function Attribution() {
  return (
    <p className="store-attribution">
      Dữ liệu và ảnh sản phẩm: <a href="https://world.openbeautyfacts.org" target="_blank" rel="noreferrer">Open Beauty Facts</a>{' '}
      (<a href="https://opendatacommons.org/licenses/odbl/1-0/" target="_blank" rel="noreferrer">ODbL</a>,{' '}
      <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noreferrer">CC BY-SA 3.0</a>). Nhãn hiệu thuộc chủ sở hữu, chỉ dùng cho mục đích học tập.
    </p>
  )
}
