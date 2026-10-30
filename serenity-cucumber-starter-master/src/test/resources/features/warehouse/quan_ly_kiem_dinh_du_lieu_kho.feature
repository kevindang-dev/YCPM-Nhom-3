@kho @kiem_dinh_du_lieu @catalog_admin
Feature: Kiểm soát tính toàn vẹn dữ liệu kho và kiểm định điều kiện mở bán sản phẩm
  Để loại trừ rủi ro khách mua nhầm sản phẩm không rõ đặc tính da gây kích ứng, bảo vệ tỷ lệ mua lại 25% và giảm đổi trả hàng xuống dưới 2%
  Là Quản trị viên danh mục & kho hàng (Catalog & Inventory Admin)
  Tôi muốn thiết lập chốt chặn mở bán khi đủ nhãn da, tự động bóc tách thành phần INCI phát hiện chất nhạy cảm, và rà soát lỗi dữ liệu kho

  Background:
    Given Quản trị viên danh mục & kho hàng đã đăng nhập vào hệ thống quản trị kho BeautyCare
    And hệ thống đã nạp từ điển các hoạt chất nhạy cảm cần cảnh báo:
      | Nhóm chất      | Tên hoạt chất INCI | Tên đồng nghĩa / thương mại | Nội dung cờ cảnh báo |
      | Cồn khô        | Alcohol Denat      | Denatured Alcohol           | Có chứa cồn khô      |
      | Cồn khô        | Ethanol            | Ethyl Alcohol               | Có chứa cồn khô      |
      | Chất bảo quản  | Methylparaben      |                             | Chứa Paraben         |
      | Chất bảo quản  | Propylparaben      |                             | Chứa Paraben         |
      | Hương liệu     | Fragrance          | Parfum                      | Chứa hương liệu      |

  # =========================================================================================
  # USER STORY 1: CHỐT CHẶN KIỂM ĐỊNH ĐIỀU KIỆN MỞ BÁN (PUBLISHING GATEKEEPER)
  # =========================================================================================
  Rule: Sản phẩm phải có tối thiểu một nhãn loại da xác định mới được cấp phép xuất kho mở bán

    @US01 @happy_path
    Scenario: Xuất bản thành công sản phẩm khi đã phân loại nhãn da đầy đủ
      Given sản phẩm "Serum B5 Phục Hồi Da" đang ở trạng thái "Bản nháp"
      And sản phẩm đã được gắn các nhãn loại da:
        | Nhãn loại da |
        | Da khô       |
        | Da nhạy cảm  |
      When Quản trị viên yêu cầu xuất bản sản phẩm "Serum B5 Phục Hồi Da"
      Then hệ thống phê duyệt điều kiện mở bán thành công
      And trạng thái của sản phẩm được cập nhật thành "Đang mở bán"
      And sản phẩm hiển thị công khai trên trang chủ bán lẻ BeautyCare
      And hệ thống gửi thông báo xác nhận xuất bản sản phẩm thành công

    @US01 @alternate_flow
    Scenario Outline: Sản phẩm mang nhãn "Mọi loại da" tự động kế thừa và xuất hiện trên tất cả các bộ lọc da đơn lẻ
      Given sản phẩm "Kem Chống Nắng Toàn Diện" đang ở trạng thái "Đang mở bán"
      And sản phẩm được gán nhãn bao hàm "Mọi loại da"
      When khách hàng tìm kiếm sản phẩm theo tiêu chí phân loại da "<Bo_Loc_Da>"
      Then hệ thống tự động kế thừa và hiển thị sản phẩm "Kem Chống Nắng Toàn Diện" trong kết quả lọc "<Bo_Loc_Da>"

      Examples:
        | Bo_Loc_Da   |
        | Da dầu      |
        | Da khô      |
        | Da hỗn hợp  |
        | Da nhạy cảm |

    @US01 @exception
    Scenario: Ngăn chặn xuất bản và cảnh báo lỗi nghiệp vụ khi bỏ trống toàn bộ nhãn da
      Given sản phẩm "Toner Cấp Ẩm Hyaluronic" đang ở trạng thái "Bản nháp"
      And sản phẩm chưa được thiết lập bất kỳ nhãn loại da nào
      When Quản trị viên cố gắng xuất bản sản phẩm "Toner Cấp Ẩm Hyaluronic"
      Then hệ thống từ chối cấp phép mở bán
      And trạng thái của sản phẩm vẫn giữ nguyên là "Bản nháp"
      And hệ thống hiển thị cảnh báo lỗi nghiệp vụ "Sản phẩm phải có ít nhất một nhãn loại da xác định trước khi mở bán"
      And sản phẩm không hiển thị trên danh mục bán lẻ trực tuyến

    @US01 @edge_case
    Scenario: Tự động thu hồi và tạm ẩn sản phẩm đang bán khi bị xóa sạch toàn bộ nhãn da
      Given sản phẩm "Sữa Rửa Mặt Dịu Nhẹ" đang có trạng thái "Đang mở bán"
      When Quản trị viên chỉnh sửa thông tin và xóa sạch toàn bộ nhãn loại da của sản phẩm
      Then hệ thống kích hoạt chốt chặn vi phạm toàn vẹn dữ liệu
      And trạng thái của sản phẩm tự động chuyển thành "Tạm ẩn"
      And sản phẩm bị gỡ bỏ ngay lập tức khỏi giao diện bán lẻ trực tuyến
      And hệ thống ghi nhận cảnh báo kiểm toán "Sản phẩm bị tạm ẩn do thiếu phân loại nhãn da"

  # =========================================================================================
  # USER STORY 2: TỰ ĐỘNG BÓC TÁCH CHUỖI INCI & KÍCH HOẠT CỜ CẢNH BÁO (AUTOMATED INCI PARSER)
  # =========================================================================================
  Rule: Phân định chính xác cồn khô gây hại và cồn béo dưỡng ẩm lành tính; kích hoạt đồng thời đa cờ cảnh báo

    @US02 @happy_path
    Scenario: Tự động kích hoạt cờ cảnh báo cồn khô khi chuỗi INCI chứa Alcohol Denat
      Given sản phẩm "Toner BHA Trị Mụn" đang được cập nhật thông tin công thức
      When Quản trị viên nạp chuỗi thành phần INCI:
        """
        Water, Alcohol Denat, Salicylic Acid, Glycerin, Sodium Hydroxide
        """
      Then hệ thống bóc tách thành phần nhận diện hoạt chất "Alcohol Denat" thuộc nhóm cồn khô
      And hệ thống tự động bật cờ cảnh báo "Có chứa cồn khô" cho sản phẩm "Toner BHA Trị Mụn"

    @US02 @alternate_flow
    Scenario: Kích hoạt đồng thời nhiều cờ cảnh báo độc lập khi công thức chứa đa hoạt chất nhạy cảm
      Given sản phẩm "Kem Dưỡng Tái Tạo Đêm" đang được kiểm định thành phần
      When Quản trị viên nạp chuỗi thành phần INCI:
        """
        Aqua, Butylene Glycol, Propylparaben, Dimethicone, Fragrance, Tocopherol
        """
      Then hệ thống tự động phân tích và kích hoạt đồng thời các cờ cảnh báo:
        | Cờ cảnh báo kích hoạt | Hoạt chất nhận diện |
        | Chứa Paraben          | Propylparaben       |
        | Chứa hương liệu       | Fragrance           |
      And hồ sơ sản phẩm hiển thị đầy đủ các cờ cảnh báo tương ứng

    @US02 @edge_case
    Scenario: Nhận diện cồn béo dưỡng ẩm lành tính và tuyệt đối không kích hoạt sai cờ cảnh báo cồn khô
      Given sản phẩm "Kem Dưỡng Ẩm Chuyên Sâu Ceramide" đang được thẩm định công thức
      When Quản trị viên nạp chuỗi thành phần INCI:
        """
        Water, Glycerin, Cetearyl Alcohol, Stearyl Alcohol, Ceramide NP, Caprylic Triglyceride
        """
      Then hệ thống nhận diện "Cetearyl Alcohol" và "Stearyl Alcohol" là cồn béo an toàn dưỡng ẩm
      And hệ thống tuyệt đối không kích hoạt cờ cảnh báo "Có chứa cồn khô"
      And sản phẩm được gắn nhãn kiểm định an toàn "Không chứa cồn khô"

    @US02 @edge_case
    Scenario: Nhận diện chính xác cồn khô thông qua tên đồng nghĩa thương mại
      Given sản phẩm "Xịt Khóa Nền Kiềm Dầu" đang được rà soát nhãn phụ
      When Quản trị viên nạp chuỗi thành phần INCI chứa tên thương mại:
        """
        Water, Denatured Alcohol, PVP, Niacinamide, Phenoxyethanol
        """
      Then hệ thống đối chiếu từ điển đồng nghĩa và xác định "Denatured Alcohol" là cồn khô
      And hệ thống tự động bật cờ cảnh báo "Có chứa cồn khô" cho sản phẩm

  # =========================================================================================
  # USER STORY 3: BẢNG KIỂM TOÁN DỮ LIỆU TỒN KHO (DATA AUDIT DASHBOARD)
  # =========================================================================================
  Rule: Lọc nhanh và hỗ trợ cập nhật tại chỗ các sản phẩm lỗi hoặc thiếu dữ liệu nhãn da/thành phần sau khi import hàng loạt từ nhà cung cấp

    @US03 @happy_path
    Scenario: Lọc danh sách sản phẩm thiếu nhãn da từ kho hàng sau khi import hàng loạt
      Given kho hàng đang quản lý 100 sản phẩm mỹ phẩm
      And có 5 sản phẩm chưa được gán nhãn loại da do nhà cung cấp cung cấp thiếu dữ liệu
      When Quản trị viên áp dụng bộ lọc kiểm toán theo tiêu chí "Chưa phân loại da"
      Then bảng kiểm toán dữ liệu trả về chính xác danh sách 5 sản phẩm thiếu thông tin
      And mỗi dòng sản phẩm trong danh sách đều hiển thị nút tác vụ "Cập nhật nhanh"

    @US03 @alternate_flow
    Scenario: Cập nhật nhãn loại da trực tiếp tại chỗ bằng cửa sổ thao tác nhanh
      Given Quản trị viên đang xem danh sách 5 sản phẩm vi phạm trên bảng kiểm toán dữ liệu
      When Quản trị viên mở cửa sổ thao tác nhanh (Quick-edit Modal) cho sản phẩm "Mặt Nạ Tràm Trà"
      And chọn bổ sung nhãn loại da:
        | Nhãn loại da |
        | Da dầu       |
        | Da mụn       |
      And xác nhận lưu thay đổi trực tiếp ngay trên cửa sổ thao tác nhanh mà không chuyển trang
      Then dữ liệu nhãn loại da của sản phẩm "Mặt Nạ Tràm Trà" được cập nhật thành công
      And sản phẩm được tự động loại khỏi danh sách vi phạm "Chưa phân loại da"
      And tổng số lượng sản phẩm cần xử lý trên bảng kiểm toán giảm từ 5 xuống còn 4

    @US03 @edge_case
    Scenario: Xuất tệp báo cáo kiểm toán dữ liệu kho sang định dạng Excel để gửi đối soát nhà phân phối
      Given bảng kiểm toán dữ liệu tồn kho đang ghi nhận danh sách các sản phẩm thiếu thông tin
      When Quản trị viên yêu cầu xuất báo cáo kiểm toán dữ liệu dưới định dạng "Excel"
      Then hệ thống tạo tệp báo cáo kiểm toán "Audit_Report_Inventory_Data.xlsx"
      And tệp báo cáo chứa đầy đủ các trường thông tin đối soát:
        | Trường thông tin bắt buộc |
        | Mã SKU sản phẩm           |
        | Tên sản phẩm              |
        | Nhà phân phối             |
        | Lỗi dữ liệu ghi nhận      |
        | Ngày nhập kho gần nhất    |
      And hệ thống lưu vết nhật ký xuất báo cáo kiểm toán vào lịch sử hoạt động quản trị kho
