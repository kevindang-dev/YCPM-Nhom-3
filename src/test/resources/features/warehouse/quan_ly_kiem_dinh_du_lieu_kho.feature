Feature: Warehouse Inventory Integrity and Product Publishing Gatekeeper
  In order to eliminate the risk of customers purchasing products without skin type classification causing allergic reactions, protect the 25% repeat purchase rate, and reduce product return rates below 2%
  As a Catalog and Inventory Admin
  I want to enforce publishing gatekeeping rules, automate INCI ingredient parsing, and audit inventory data quality

  Background:
    Given Quản trị viên đã đăng nhập vào hệ thống quản trị kho BeautyCare
    And hệ thống đã thiết lập danh mục hoạt chất nhạy cảm cảnh báo gồm:
      | Nhom_Chat     | Tu_Khoa_Nhan_Dien            | Co_Canh_Bao_Kich_Hoat |
      | Cồn khô       | Alcohol Denat, Ethanol       | Có chứa cồn khô       |
      | Hương liệu    | Fragrance, Parfum            | Chứa hương liệu       |
      | Chất bảo quản | Methylparaben, Propylparaben | Chứa Paraben          |

  # =========================================================================
  # USER STORY 1: STOCK RELEASE & PUBLISHING GATEKEEPER
  # =========================================================================
  Rule: A product must have at least one valid skin type tag to be eligible for publishing

    # 1.1 HAPPY PATH
    Scenario: Successfully publish product when valid skin type tags are assigned
      Given sản phẩm "Serum La Roche-Posay B5" trong kho đang ở trạng thái "Bản nháp"
      And sản phẩm đã được gắn nhãn loại da gồm "Da khô", "Da nhạy cảm"
      When Quản trị viên thực hiện xuất bản sản phẩm "Serum La Roche-Posay B5"
      Then trạng thái mở bán của sản phẩm phải là "Đang mở bán"
      And hệ thống đưa ra thông báo: "Xuất bản sản phẩm thành công"
      And sản phẩm xuất hiện trong danh sách hiển thị của bộ lọc "Da nhạy cảm" ngoài website

    # 1.2 ALTERNATE FLOW
    Scenario Outline: Products tagged with All Skin Types automatically inherit to all specific filters
      Given sản phẩm "Sữa Rửa Mặt CeraVe" trong kho đang ở trạng thái "Đang mở bán" với nhãn "Mọi loại da"
      When khách hàng sử dụng bộ lọc tìm kiếm theo tiêu chí "<Bo_Loc_Khach_Chon>"
      Then sản phẩm "Sữa Rửa Mặt CeraVe" phải hiển thị trong kết quả là "<Trang_Thai_Hien_Thi>"

      Examples:
        | Bo_Loc_Khach_Chon | Trang_Thai_Hien_Thi | Ghi chú                          |
        | Da dầu            | Có xuất hiện        | Kế thừa hiển thị cho da dầu      |
        | Da khô            | Có xuất hiện        | Kế thừa hiển thị cho da khô      |
        | Da hỗn hợp        | Có xuất hiện        | Kế thừa hiển thị cho da hỗn hợp  |
        | Da nhạy cảm       | Có xuất hiện        | Kế thừa an toàn cho da nhạy cảm  |

    # 1.3 EXCEPTION
    Scenario: Reject publishing and enforce Draft status when skin type tags are missing
      Given sản phẩm "Kem Chống Nắng Anessa" trong kho đang ở trạng thái "Bản nháp"
      And sản phẩm chưa được thiết lập bất kỳ nhãn loại da nào
      When Quản trị viên thực hiện xuất bản sản phẩm "Kem Chống Nắng Anessa"
      Then hệ thống từ chối cấp phép mở bán cho sản phẩm
      And trạng thái của sản phẩm vẫn được giữ nguyên là "Bản nháp"
      And hệ thống hiển thị cảnh báo: "Từ chối mở bán: Vui lòng chọn ít nhất một nhãn loại da phù hợp"
      And sản phẩm không được xuất hiện trên thanh tìm kiếm ngoài website

    # 1.4 EDGE CASE
    Scenario: Automatically revoke publishing status if all skin type tags are removed
      Given sản phẩm "Toner Tràm Trà" trong kho đang ở trạng thái "Đang mở bán" với nhãn "Da dầu"
      When Quản trị viên cập nhật nhãn loại da của sản phẩm "Toner Tràm Trà" thành danh sách rỗng
      Then hệ thống từ chối lưu thay đổi rỗng
      And tự động thu hồi quyền mở bán, chuyển trạng thái sản phẩm thành "Tạm ẩn"
      And sản phẩm lập tức biến mất khỏi danh mục hiển thị ngoài website

  # =========================================================================
  # USER STORY 2: AUTOMATED INCI PARSER & HAZARD INSPECTION
  # =========================================================================
  Rule: Automatically distinguish drying alcohols from fatty alcohols and activate hazard flags

    # 2.1 HAPPY PATH
    Scenario: Parse raw ingredients and flag drying alcohol when Alcohol Denat is detected
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Toner Trị Mụn Some By Mi":
        """
        Water, Alcohol Denat, Salicylic Acid, Melaleuca Alternifolia Leaf Water
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Toner Trị Mụn Some By Mi"
      Then hệ thống bóc tách thành công 4 hoạt chất riêng biệt
      And kích hoạt cờ cảnh báo "Có chứa cồn khô" cho sản phẩm "Toner Trị Mụn Some By Mi"

    # 2.2 ALTERNATE FLOW
    Scenario: Simultaneously trigger multiple warning flags when multiple hazardous ingredients exist
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Kem Dưỡng Ban Đêm":
        """
        Aqua, Glycerin, Propylparaben, Fragrance, Dimethicone
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Kem Dưỡng Ban Đêm"
      Then hệ thống kích hoạt đồng thời 2 cờ cảnh báo gồm "Chứa Paraben" và "Chứa hương liệu"

    # 2.3 EDGE CASE - SAFE FATTY ALCOHOL
    Scenario: Correctly recognize safe fatty alcohols without triggering false drying alcohol warnings
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe":
        """
        Aqua, Cetearyl Alcohol, Stearyl Alcohol, Ceramide NP, Glycerin
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe"
      Then hệ thống xác nhận sản phẩm đạt trạng thái an toàn "Lành tính"
      And tuyệt đối không kích hoạt cờ cảnh báo "Có chứa cồn khô"

    # 2.4 EDGE CASE - ALIAS MATCHING
    Scenario: Recognize hazardous ingredients via international cosmetic aliases
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Xịt Khoáng Cấp Ẩm":
        """
        Aqua, Denatured Alcohol, Nitrogen, Glycerin
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Xịt Khoáng Cấp Ẩm"
      Then hệ thống nhận diện hoạt chất "Denatured Alcohol" tương đương với cồn khô
      And kích hoạt cờ cảnh báo "Có chứa cồn khô" cho sản phẩm "Xịt Khoáng Cấp Ẩm"

  # =========================================================================
  # USER STORY 3: INVENTORY DATA AUDIT DASHBOARD
  # =========================================================================
  Rule: Support rapid audit and in-place resolution for unclassified inventory data

    # 3.1 HAPPY PATH
    Scenario: Accurately filter products missing skin type classification in inventory
      Given cơ sở dữ liệu kho đang lưu trữ 3 sản phẩm gồm:
        | Ten_San_Pham        | Nhan_Loai_Da | Trang_Thai   |
        | Serum B5 Phục Hồi   | Da khô       | Đang mở bán  |
        | Kem Dưỡng Trắng Da  |              | Bản nháp     |
        | Tẩy Tế Bào Chết AHA |              | Bản nháp     |
      When Quản trị viên thực hiện lọc danh sách theo điều kiện "Chưa phân loại da"
      Then hệ thống trả về kết quả gồm đúng 2 sản phẩm cần xử lý
      And danh sách kết quả chứa sản phẩm "Kem Dưỡng Trắng Da" và "Tẩy Tế Bào Chết AHA"

    # 3.2 ALTERNATE FLOW
    Scenario: Quick-update skin type classification directly on the audit dashboard
      Given sản phẩm "Kem Dưỡng Trắng Da" đang nằm trong danh sách cảnh báo thiếu nhãn da
      When Quản trị viên thực hiện cập nhật nhanh nhãn loại da "Da dầu" cho sản phẩm "Kem Dưỡng Trắng Da"
      Then sản phẩm "Kem Dưỡng Trắng Da" tự động biến mất khỏi danh sách cảnh báo thiếu nhãn da
      And nhãn loại da của sản phẩm "Kem Dưỡng Trắng Da" trong kho được ghi nhận là "Da dầu"

    # 3.3 EDGE CASE - REPORT EXPORT
    Scenario: Export inventory data audit report to Excel for supplier feedback
      Given có ít nhất một sản phẩm trong kho đang bị thiếu nhãn loại da
      When Quản trị viên yêu cầu kết xuất báo cáo kiểm toán dữ liệu kho
      Then hệ thống tạo ra tệp báo cáo dạng bảng tính với định dạng "XLSX"
      And nội dung báo cáo thống kê đầy đủ mã sản phẩm và lý do lỗi dữ liệu
