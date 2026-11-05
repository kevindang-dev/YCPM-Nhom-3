Feature: Warehouse Inventory Integrity and Product Publishing Gatekeeper
  In order to loại trừ rủi ro khách hàng mua nhầm sản phẩm thiếu nhãn da gây kích ứng, bảo vệ mục tiêu mua lại 25% và giảm khiếu nại đổi trả dưới 2%
  As a Quản trị viên kho và danh mục sản phẩm (Catalog and Inventory Admin)
  I want to thiết lập cơ chế kiểm định điều kiện mở bán, tự động bóc tách thành phần INCI và rà soát tồn kho dữ liệu

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
  Rule: Sản phẩm phải có tối thiểu một nhãn loại da xác định mới được cấp phép mở bán

    # 1.1 HAPPY PATH
    Scenario: Cấp phép mở bán thành công khi sản phẩm có đầy đủ nhãn loại da
      Given sản phẩm "Serum La Roche-Posay B5" trong kho đang ở trạng thái "Bản nháp"
      And sản phẩm "Serum La Roche-Posay B5" đã được gắn nhãn loại da gồm "Da khô", "Da nhạy cảm"
      When Quản trị viên thực hiện xuất bản sản phẩm "Serum La Roche-Posay B5"
      Then trạng thái mở bán của sản phẩm "Serum La Roche-Posay B5" phải là "Đang mở bán"
      And hệ thống đưa ra thông báo: "Xuất bản sản phẩm thành công"
      And sản phẩm "Serum La Roche-Posay B5" xuất hiện trong danh sách hiển thị của bộ lọc "Da nhạy cảm" ngoài website

    # 1.2 ALTERNATE FLOW
    Scenario Outline: Sản phẩm mang nhãn bao hàm tự động tương thích với toàn bộ bộ lọc đơn lẻ
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
    Scenario: Từ chối xuất bản và cưỡng chế trạng thái Bản nháp khi sản phẩm bỏ trống nhãn loại da
      Given sản phẩm "Kem Chống Nắng Anessa" trong kho đang ở trạng thái "Bản nháp"
      And sản phẩm "Kem Chống Nắng Anessa" chưa được thiết lập bất kỳ nhãn loại da nào
      When Quản trị viên thực hiện xuất bản sản phẩm "Kem Chống Nắng Anessa"
      Then hệ thống từ chối cấp phép mở bán cho sản phẩm
      And trạng thái của sản phẩm "Kem Chống Nắng Anessa" vẫn được giữ nguyên là "Bản nháp"
      And hệ thống hiển thị cảnh báo: "Từ chối mở bán: Vui lòng chọn ít nhất một nhãn loại da phù hợp"
      And sản phẩm "Kem Chống Nắng Anessa" không được xuất hiện trên thanh tìm kiếm ngoài website

    # 1.4 EDGE CASE
    Scenario: Tự động thu hồi quyền mở bán nếu sản phẩm đang bán bị xóa sạch nhãn loại da
      Given sản phẩm "Toner Tràm Trà" trong kho đang ở trạng thái "Đang mở bán" với nhãn "Da dầu"
      When Quản trị viên cập nhật nhãn loại da của sản phẩm "Toner Tràm Trà" thành danh sách rỗng
      Then hệ thống từ chối lưu thay đổi rỗng
      And tự động thu hồi quyền mở bán, chuyển trạng thái sản phẩm "Toner Tràm Trà" thành "Tạm ẩn"
      And sản phẩm "Toner Tràm Trà" lập tức biến mất khỏi danh mục hiển thị ngoài website

  # =========================================================================
  # USER STORY 2: AUTOMATED INCI PARSER & HAZARD INSPECTION
  # =========================================================================
  Rule: Tự động phân định chính xác cồn khô gây hại và cồn béo an toàn để kích hoạt cờ cảnh báo

    # 2.1 HAPPY PATH
    Scenario: Tự động bóc tách chuỗi và kích hoạt cờ cảnh báo cồn khô khi phát hiện Alcohol Denat
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Toner Trị Mụn Some By Mi":
        """
        Water, Alcohol Denat, Salicylic Acid, Melaleuca Alternifolia Leaf Water
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Toner Trị Mụn Some By Mi"
      Then hệ thống bóc tách thành công 4 hoạt chất riêng biệt cho sản phẩm "Toner Trị Mụn Some By Mi"
      And kích hoạt cờ cảnh báo "Có chứa cồn khô" cho sản phẩm "Toner Trị Mụn Some By Mi"

    # 2.2 ALTERNATE FLOW
    Scenario: Kích hoạt đồng thời nhiều cờ cảnh báo độc lập khi sản phẩm chứa nhiều chất nhạy cảm
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Kem Dưỡng Ban Đêm":
        """
        Aqua, Glycerin, Propylparaben, Fragrance, Dimethicone
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Kem Dưỡng Ban Đêm"
      Then hệ thống kích hoạt đồng thời 2 cờ cảnh báo gồm "Chứa Paraben" và "Chứa hương liệu" cho sản phẩm "Kem Dưỡng Ban Đêm"

    # 2.3 EDGE CASE - SAFE FATTY ALCOHOL
    Scenario: Nhận diện chính xác cồn béo dưỡng ẩm và không kích hoạt cảnh báo cồn khô
      Given Quản trị viên nhập chuỗi thành phần cho sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe":
        """
        Aqua, Cetearyl Alcohol, Stearyl Alcohol, Ceramide NP, Glycerin
        """
      When hệ thống tiến hành thẩm định thành phần cho sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe"
      Then hệ thống xác nhận sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe" đạt trạng thái an toàn "Lành tính"
      And tuyệt đối không kích hoạt cờ cảnh báo "Có chứa cồn khô" cho sản phẩm "Kem Dưỡng Ẩm Phục Hồi CeraVe"

    # 2.4 EDGE CASE - ALIAS MATCHING
    Scenario: Nhận diện hoạt chất cảnh báo dù nhà sản xuất sử dụng tên thương mại quốc tế
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
  Rule: Hệ thống hỗ trợ rà soát và khắc phục nhanh các sản phẩm lỗi dữ liệu sau khi nhập kho

    # 3.1 HAPPY PATH
    Scenario: Lọc chính xác danh sách các sản phẩm đang thiếu nhãn loại da trong kho
      Given cơ sở dữ liệu kho đang lưu trữ 3 sản phẩm gồm:
        | Ten_San_Pham        | Nhan_Loai_Da | Trang_Thai   |
        | Serum B5 Phục Hồi   | Da khô       | Đang mở bán  |
        | Kem Dưỡng Trắng Da  |              | Bản nháp     |
        | Tẩy Tế Bào Chết AHA |              | Bản nháp     |
      When Quản trị viên thực hiện lọc danh sách theo điều kiện "Chưa phân loại da"
      Then hệ thống trả về kết quả gồm đúng 2 sản phẩm cần xử lý
      And danh sách kết quả chứa sản phẩm "Kem Dưỡng Trắng Da" và "Tẩy Tế Bào Chết AHA"

    # 3.2 ALTERNATE FLOW
    Scenario: Cập nhật nhãn loại da nhanh tại chỗ trên bảng kiểm toán dữ liệu
      Given sản phẩm "Kem Dưỡng Trắng Da" đang nằm trong danh sách cảnh báo thiếu nhãn da
      When Quản trị viên thực hiện cập nhật nhanh nhãn loại da "Da dầu" cho sản phẩm "Kem Dưỡng Trắng Da"
      Then sản phẩm "Kem Dưỡng Trắng Da" tự động biến mất khỏi danh sách cảnh báo thiếu nhãn da
      And nhãn loại da của sản phẩm "Kem Dưỡng Trắng Da" trong kho được ghi nhận là "Da dầu"

    # 3.3 EDGE CASE - REPORT EXPORT
    Scenario: Xuất dữ liệu kiểm toán tồn kho phục vụ phản hồi nhà cung cấp
      Given có ít nhất một sản phẩm trong kho đang bị thiếu nhãn loại da
      When Quản trị viên yêu cầu kết xuất báo cáo kiểm toán dữ liệu kho
      Then hệ thống tạo ra tệp báo cáo dạng bảng tính với định dạng "XLSX"
      And nội dung báo cáo thống kê đầy đủ mã sản phẩm và lý do lỗi dữ liệu
