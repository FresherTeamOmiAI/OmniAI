package com.omniai.profile;

import java.util.List;

/**
 * Body PUT hồ sơ da (web và ai-brain dùng chung). Kiểm ở SkinProfileService để gom mọi lỗi vào {@code errors[]}.
 *
 * @param skinType     mã loại da hoặc null/rỗng (chưa nói)
 * @param skinConcerns mã vấn đề da (bảng skin_concern), null = không có
 * @param allergies    dị ứng khách tự nêu, tối đa 10, mỗi mục 1-60 ký tự
 * @param pregnant     mang thai hoặc cho con bú; null = false
 * @param consent      bắt buộc true: khách đồng ý cho shop lưu dữ liệu sức khoẻ này
 */
public record SkinProfileRequest(String skinType, List<String> skinConcerns, List<String> allergies, Boolean pregnant,
                                 Boolean consent) {
}
