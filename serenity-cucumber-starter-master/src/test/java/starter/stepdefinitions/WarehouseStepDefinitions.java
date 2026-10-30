package starter.stepdefinitions;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.ProductStatus;
import com.beautycare.warehouse.model.SkinTypeTag;
import com.beautycare.warehouse.model.WarningFlag;
import com.beautycare.warehouse.repository.InMemoryProductRepository;
import com.beautycare.warehouse.repository.ProductRepository;
import com.beautycare.warehouse.service.HazardDictionary;
import com.beautycare.warehouse.service.InciParserService;
import com.beautycare.warehouse.service.InventoryAuditService;
import com.beautycare.warehouse.service.PublishingGatekeeper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class WarehouseStepDefinitions {

    private ProductRepository productRepository;
    private HazardDictionary hazardDictionary;
    private InciParserService inciParserService;
    private PublishingGatekeeper publishingGatekeeper;
    private InventoryAuditService inventoryAuditService;

    // Test context state
    private Product currentProduct;
    private PublishingGatekeeper.PublishingResponse lastPublishResponse;
    private List<Product> searchResults;
    private InciParserService.ParseResult lastParseResult;
    private List<Product> filteredAuditProducts;
    private Set<SkinTypeTag> pendingSkinTypes;
    private InventoryAuditService.AuditReport lastExportedReport;

    @Before
    public void setUp() {
        productRepository = new InMemoryProductRepository();
        hazardDictionary = new HazardDictionary();
        inciParserService = new InciParserService(hazardDictionary);
        publishingGatekeeper = new PublishingGatekeeper(productRepository);
        inventoryAuditService = new InventoryAuditService(productRepository);

        currentProduct = null;
        lastPublishResponse = null;
        searchResults = new ArrayList<>();
        lastParseResult = null;
        filteredAuditProducts = new ArrayList<>();
        pendingSkinTypes = new LinkedHashSet<>();
        lastExportedReport = null;
    }

    // =========================================================================================
    // BACKGROUND STEPS
    // =========================================================================================

    @Given("Quản trị viên danh mục & kho hàng đã đăng nhập vào hệ thống quản trị kho BeautyCare")
    public void quan_tri_vien_da_dang_nhap() {
        assertThat(publishingGatekeeper).isNotNull();
        assertThat(productRepository).isNotNull();
    }

    @Given("hệ thống đã nạp từ điển các hoạt chất nhạy cảm cần cảnh báo:")
    public void he_thong_da_nap_tu_dien_hoat_chat(DataTable dataTable) {
        hazardDictionary.clear();
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> row : rows) {
            String category = row.get("Nhóm chất");
            String inci = row.get("Tên hoạt chất INCI");
            String alias = row.get("Tên đồng nghĩa / thương mại");
            String flagName = row.get("Nội dung cờ cảnh báo");
            hazardDictionary.addEntry(category, inci, alias, WarningFlag.of(flagName));
        }
    }

    // =========================================================================================
    // USER STORY 1: PUBLISHING GATEKEEPER
    // =========================================================================================

    @Given("sản phẩm {string} đang ở trạng thái {string}")
    public void san_pham_dang_o_trang_thai(String productName, String statusName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.setStatus(ProductStatus.fromVietnamese(statusName));
        productRepository.save(currentProduct);
    }

    @Given("sản phẩm đã được gắn các nhãn loại da:")
    public void san_pham_da_duoc_gan_cac_nhan_loai_da(DataTable dataTable) {
        assertThat(currentProduct).isNotNull();
        List<String> tags = dataTable.asList();
        // Bỏ qua dòng header nếu có "Nhãn loại da"
        for (String tag : tags) {
            if (!"Nhãn loại da".equalsIgnoreCase(tag.trim())) {
                currentProduct.addSkinType(SkinTypeTag.of(tag));
            }
        }
        productRepository.save(currentProduct);
    }

    @When("Quản trị viên yêu cầu xuất bản sản phẩm {string}")
    public void quan_tri_vien_yeu_cau_xuat_ban_san_pham(String productName) {
        lastPublishResponse = publishingGatekeeper.requestPublish(productName);
        if (lastPublishResponse.getProduct() != null) {
            currentProduct = lastPublishResponse.getProduct();
        }
    }

    @Then("hệ thống phê duyệt điều kiện mở bán thành công")
    public void he_thong_phe_duyet_dieu_kien_mo_ban_thanh_cong() {
        assertThat(lastPublishResponse).isNotNull();
        assertThat(lastPublishResponse.isSuccess()).isTrue();
    }

    @Then("trạng thái của sản phẩm được cập nhật thành {string}")
    public void trang_thai_cua_san_pham_duoc_cap_nhat_thanh(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("sản phẩm hiển thị công khai trên trang chủ bán lẻ BeautyCare")
    public void san_pham_hien_thi_cong_khai_tren_trang_chu() {
        assertThat(currentProduct.isVisibleOnStorefront()).isTrue();
    }

    @Then("hệ thống gửi thông báo xác nhận xuất bản sản phẩm thành công")
    public void he_thong_gui_thong_bao_xac_nhan_xuat_ban_thanh_cong() {
        assertThat(lastPublishResponse.getMessage()).contains("Xuất bản sản phẩm thành công");
    }

    @Given("sản phẩm được gán nhãn bao hàm {string}")
    public void san_pham_duoc_gan_nhan_bao_ham(String label) {
        assertThat(currentProduct).isNotNull();
        currentProduct.addSkinType(SkinTypeTag.of(label));
        productRepository.save(currentProduct);
    }

    @When("^khách hàng tìm kiếm sản phẩm theo tiêu chí phân loại da \"?([^\"]+)\"?$")
    public void khach_hang_tim_kiem_san_pham_theo_tieu_chi(String filterCriterion) {
        searchResults = publishingGatekeeper.searchProductsBySkinType(SkinTypeTag.of(filterCriterion));
    }

    @Then("^hệ thống tự động kế thừa và hiển thị sản phẩm \"([^\"]+)\" trong kết quả lọc \"?([^\"]+)\"?$")
    public void he_thong_tu_dong_ke_thua_va_hien_thi_san_pham(String productName, String filterCriterion) {
        List<String> resultNames = searchResults.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(resultNames).contains(productName);
    }

    @Given("sản phẩm chưa được thiết lập bất kỳ nhãn loại da nào")
    public void san_pham_chua_duoc_thiet_lap_bat_ky_nhan_loai_da_nao() {
        assertThat(currentProduct).isNotNull();
        currentProduct.clearSkinTypes();
        productRepository.save(currentProduct);
    }

    @When("Quản trị viên cố gắng xuất bản sản phẩm {string}")
    public void quan_tri_vien_co_gang_xuat_ban_san_pham(String productName) {
        lastPublishResponse = publishingGatekeeper.requestPublish(productName);
        if (lastPublishResponse.getProduct() != null) {
            currentProduct = lastPublishResponse.getProduct();
        }
    }

    @Then("hệ thống từ chối cấp phép mở bán")
    public void he_thong_tu_choi_cap_phep_mo_ban() {
        assertThat(lastPublishResponse).isNotNull();
        assertThat(lastPublishResponse.isSuccess()).isFalse();
    }

    @Then("trạng thái của sản phẩm vẫn giữ nguyên là {string}")
    public void trang_thai_cua_san_pham_van_giu_nguyen_la(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("hệ thống hiển thị cảnh báo lỗi nghiệp vụ {string}")
    public void he_thong_hien_thi_canh_bao_loi_nghiep_vu(String expectedError) {
        assertThat(lastPublishResponse.getMessage()).isEqualTo(expectedError);
    }

    @Then("sản phẩm không hiển thị trên danh mục bán lẻ trực tuyến")
    public void san_pham_khong_hien_thi_tren_danh_muc_ban_le() {
        assertThat(currentProduct.isVisibleOnStorefront()).isFalse();
    }

    @Given("sản phẩm {string} đang có trạng thái {string}")
    public void san_pham_dang_co_trang_thai(String productName, String statusName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.setStatus(ProductStatus.fromVietnamese(statusName));
        currentProduct.addSkinType(SkinTypeTag.DA_NHAY_CAM);
        productRepository.save(currentProduct);
    }

    @When("Quản trị viên chỉnh sửa thông tin và xóa sạch toàn bộ nhãn loại da của sản phẩm")
    public void quan_tri_vien_chinh_sua_va_xoa_sach_nhan_da() {
        currentProduct.clearSkinTypes();
        publishingGatekeeper.handleProductSkinTypesRemoved(currentProduct);
    }

    @Then("hệ thống kích hoạt chốt chặn vi phạm toàn vẹn dữ liệu")
    public void he_thong_kich_hoat_chot_chan_vi_pham() {
        assertThat(currentProduct.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Then("trạng thái của sản phẩm tự động chuyển thành {string}")
    public void trang_thai_cua_san_pham_tu_dong_chuyen_thanh(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("sản phẩm bị gỡ bỏ ngay lập tức khỏi giao diện bán lẻ trực tuyến")
    public void san_pham_bi_go_bo_khoi_giao_dien() {
        assertThat(currentProduct.isVisibleOnStorefront()).isFalse();
    }

    @Then("hệ thống ghi nhận cảnh báo kiểm toán {string}")
    public void he_thong_ghi_nhan_canh_bao_kiem_toan(String expectedLog) {
        assertThat(currentProduct.getAuditLogs()).contains(expectedLog);
    }

    // =========================================================================================
    // USER STORY 2: AUTOMATED INCI PARSER
    // =========================================================================================

    @Given("sản phẩm {string} đang được cập nhật thông tin công thức")
    public void san_pham_dang_duoc_cap_nhat_thong_tin_cong_thuc(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
    }

    @When("Quản trị viên nạp chuỗi thành phần INCI:")
    public void quan_tri_vien_nap_chuoi_thanh_phan_inci(String docString) {
        lastParseResult = inciParserService.parseAndApplyToProduct(currentProduct, docString);
        productRepository.save(currentProduct);
    }

    @Then("hệ thống bóc tách thành phần nhận diện hoạt chất {string} thuộc nhóm cồn khô")
    public void he_thong_boc_tach_nhan_dien_hoat_chat_con_kho(String ingredientName) {
        assertThat(lastParseResult).isNotNull();
        assertThat(lastParseResult.getDetectedHazards()).containsKey(WarningFlag.CO_CHUA_CON_KHO);
        assertThat(lastParseResult.getDetectedHazards().get(WarningFlag.CO_CHUA_CON_KHO))
                .isEqualToIgnoringCase(ingredientName);
    }

    @Then("hệ thống tự động bật cờ cảnh báo {string} cho sản phẩm {string}")
    public void he_thong_tu_dong_bat_co_canh_bao_cho_san_pham(String flagName, String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p.hasWarningFlag(WarningFlag.of(flagName))).isTrue();
    }

    @Then("hệ thống tự động bật cờ cảnh báo {string} cho sản phẩm")
    public void he_thong_tu_dong_bat_co_canh_bao_cho_san_pham_hien_tai(String flagName) {
        assertThat(currentProduct.hasWarningFlag(WarningFlag.of(flagName))).isTrue();
    }

    @Given("sản phẩm {string} đang được kiểm định thành phần")
    public void san_pham_dang_duoc_kiem_dinh_thanh_phan(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
    }

    @Then("hệ thống tự động phân tích và kích hoạt đồng thời các cờ cảnh báo:")
    public void he_thong_tu_dong_phan_tich_va_kich_hoat_dong_thoi_cac_co_canh_bao(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> row : rows) {
            String flagName = row.get("Cờ cảnh báo kích hoạt");
            String ingredientName = row.get("Hoạt chất nhận diện");

            WarningFlag flag = WarningFlag.of(flagName);
            assertThat(lastParseResult.getDetectedHazards()).containsKey(flag);
            assertThat(lastParseResult.getDetectedHazards().get(flag))
                    .isEqualToIgnoringCase(ingredientName);
        }
    }

    @Then("hồ sơ sản phẩm hiển thị đầy đủ các cờ cảnh báo tương ứng")
    public void ho_so_san_pham_hien_thi_day_du_cac_co_canh_bao() {
        assertThat(currentProduct.getWarningFlags()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Given("sản phẩm {string} đang được thẩm định công thức")
    public void san_pham_dang_duoc_tham_dinh_cong_thuc(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
    }

    @Then("hệ thống nhận diện {string} và {string} là cồn béo an toàn dưỡng ẩm")
    public void he_thong_nhan_dien_la_con_beo_an_toan(String ing1, String ing2) {
        assertThat(inciParserService.isSafeFattyAlcohol(ing1)).isTrue();
        assertThat(inciParserService.isSafeFattyAlcohol(ing2)).isTrue();
    }

    @Then("hệ thống tuyệt đối không kích hoạt cờ cảnh báo {string}")
    public void he_thong_tuyet_doi_khong_kich_hoat_co_canh_bao(String flagName) {
        assertThat(currentProduct.hasWarningFlag(WarningFlag.of(flagName))).isFalse();
    }

    @Then("sản phẩm được gắn nhãn kiểm định an toàn {string}")
    public void san_pham_duoc_gan_nhan_kiem_dinh_an_toan(String safetyLabel) {
        assertThat(currentProduct.hasSafetyLabel(safetyLabel)).isTrue();
    }

    @Given("sản phẩm {string} đang được rà soát nhãn phụ")
    public void san_pham_dang_duoc_ra_soat_nhan_phu(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
    }

    @When("Quản trị viên nạp chuỗi thành phần INCI chứa tên thương mại:")
    public void quan_tri_vien_nap_chuoi_thanh_phan_inci_chua_ten_thuong_mai(String docString) {
        lastParseResult = inciParserService.parseAndApplyToProduct(currentProduct, docString);
        productRepository.save(currentProduct);
    }

    @Then("hệ thống đối chiếu từ điển đồng nghĩa và xác định {string} là cồn khô")
    public void he_thong_doi_chieu_tu_dien_dong_nghia(String aliasName) {
        Optional<HazardDictionary.HazardEntry> hazardOpt = inciParserService.findHazard(aliasName);
        assertThat(hazardOpt).isPresent();
        assertThat(hazardOpt.get().getCategory()).isEqualTo("Cồn khô");
    }

    // =========================================================================================
    // USER STORY 3: DATA AUDIT DASHBOARD
    // =========================================================================================

    @Given("kho hàng đang quản lý {int} sản phẩm mỹ phẩm")
    public void kho_hang_dang_quan_ly_san_pham(int totalCount) {
        productRepository.clear();
        for (int i = 1; i <= totalCount; i++) {
            Product p = new Product("SKU-" + String.format("%03d", i), "Sản phẩm " + i, "NPP BeautyCare", null, "2026-10-20");
            p.addSkinType(SkinTypeTag.DA_DAU);
            productRepository.save(p);
        }
    }

    @Given("có {int} sản phẩm chưa được gán nhãn loại da do nhà cung cấp cung cấp thiếu dữ liệu")
    public void co_san_pham_chua_duoc_gan_nhan_loai_da(int missingCount) {
        List<Product> products = productRepository.findAll();
        for (int i = 0; i < missingCount && i < products.size(); i++) {
            Product p = products.get(i);
            p.clearSkinTypes();
            p.setAuditError("Chưa phân loại da");
            productRepository.save(p);
        }
    }

    @When("Quản trị viên áp dụng bộ lọc kiểm toán theo tiêu chí {string}")
    public void quan_tri_vien_ap_dung_bo_loc_kiem_toan(String criterion) {
        filteredAuditProducts = inventoryAuditService.filterByAuditCriterion(criterion);
    }

    @Then("bảng kiểm toán dữ liệu trả về chính xác danh sách {int} sản phẩm thiếu thông tin")
    public void bang_kiem_toan_du_lieu_tra_ve_chinh_xac(int expectedCount) {
        assertThat(filteredAuditProducts).hasSize(expectedCount);
    }

    @Then("mỗi dòng sản phẩm trong danh sách đều hiển thị nút tác vụ {string}")
    public void moi_dong_san_pham_deu_hien_thi_nut_tac_vu(String actionName) {
        assertThat(actionName).isEqualTo("Cập nhật nhanh");
        assertThat(filteredAuditProducts).isNotEmpty();
    }

    @Given("Quản trị viên đang xem danh sách {int} sản phẩm vi phạm trên bảng kiểm toán dữ liệu")
    public void quan_tri_vien_dang_xem_danh_sach_san_pham_vi_pham(int count) {
        productRepository.clear();
        Product target = new Product("SKU-001", "Mặt Nạ Tràm Trà", "NPP GreenLife", "Chưa phân loại da", "2026-10-21");
        productRepository.save(target);

        for (int i = 2; i <= count; i++) {
            Product p = new Product("SKU-" + String.format("%03d", i), "Sản phẩm lỗi " + i, "NPP BeautyCare", "Chưa phân loại da", "2026-10-20");
            productRepository.save(p);
        }

        filteredAuditProducts = inventoryAuditService.filterByAuditCriterion("Chưa phân loại da");
        assertThat(filteredAuditProducts).hasSize(count);
    }

    @When("Quản trị viên mở cửa sổ thao tác nhanh \\(Quick-edit Modal) cho sản phẩm {string}")
    public void quan_tri_vien_mo_cua_so_thao_tac_nhanh(String productName) {
        currentProduct = productRepository.findByName(productName).orElseThrow(
                () -> new AssertionError("Không tìm thấy sản phẩm " + productName)
        );
        pendingSkinTypes.clear();
    }

    @When("chọn bổ sung nhãn loại da:")
    public void chon_bo_sung_nhan_loai_da(DataTable dataTable) {
        List<String> tags = dataTable.asList();
        for (String tag : tags) {
            if (!"Nhãn loại da".equalsIgnoreCase(tag.trim())) {
                pendingSkinTypes.add(SkinTypeTag.of(tag));
            }
        }
    }

    @When("xác nhận lưu thay đổi trực tiếp ngay trên cửa sổ thao tác nhanh mà không chuyển trang")
    public void xac_nhan_luu_thay_doi_truc_tiep_ngay_tren_cua_so() {
        inventoryAuditService.quickEditSkinTypes(currentProduct.getName(), pendingSkinTypes);
    }

    @Then("dữ liệu nhãn loại da của sản phẩm {string} được cập nhật thành công")
    public void du_lieu_nhan_loai_da_duoc_cap_nhat_thanh_cong(String productName) {
        Product updated = productRepository.findByName(productName).orElseThrow(
                () -> new AssertionError("Không tìm thấy sản phẩm " + productName)
        );
        assertThat(updated.getSkinTypes()).isNotEmpty();
        assertThat(updated.getSkinTypes()).containsAll(pendingSkinTypes);
    }

    @Then("sản phẩm được tự động loại khỏi danh sách vi phạm {string}")
    public void san_pham_duoc_tu_dong_loai_khoi_danh_sach_vi_pham(String criterion) {
        List<Product> violations = inventoryAuditService.filterByAuditCriterion(criterion);
        List<String> violationNames = violations.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(violationNames).doesNotContain(currentProduct.getName());
    }

    @Then("tổng số lượng sản phẩm cần xử lý trên bảng kiểm toán giảm từ {int} xuống còn {int}")
    public void tong_so_luong_san_pham_can_xu_ly_giam(int oldCount, int newCount) {
        List<Product> violations = inventoryAuditService.filterByAuditCriterion("Chưa phân loại da");
        assertThat(violations).hasSize(newCount);
    }

    @Given("bảng kiểm toán dữ liệu tồn kho đang ghi nhận danh sách các sản phẩm thiếu thông tin")
    public void bang_kiem_toan_du_lieu_dang_ghi_nhan_danh_sach() {
        productRepository.clear();
        Product p1 = new Product("SKU-101", "Serum Trắng Da Vitamin C", "NPP Hàn Quốc", "Chưa phân loại da", "2026-10-18");
        Product p2 = new Product("SKU-102", "Kem Nền BB Cream", "NPP Nhật Bản", "Chưa phân loại da", "2026-10-19");
        productRepository.save(p1);
        productRepository.save(p2);
    }

    @When("Quản trị viên yêu cầu xuất báo cáo kiểm toán dữ liệu dưới định dạng {string}")
    public void quan_tri_vien_yeu_cau_xuat_bao_cao_kiem_toan(String format) {
        lastExportedReport = inventoryAuditService.exportAuditReport(format);
    }

    @Then("hệ thống tạo tệp báo cáo kiểm toán {string}")
    public void he_thong_tao_tep_bao_cao_kiem_toan(String expectedFileName) {
        assertThat(lastExportedReport).isNotNull();
        assertThat(lastExportedReport.getFileName()).isEqualTo(expectedFileName);
    }

    @Then("tệp báo cáo chứa đầy đủ các trường thông tin đối soát:")
    public void tep_bao_cao_chua_day_du_cac_truong_thong_tin(DataTable dataTable) {
        assertThat(lastExportedReport.getRows()).isNotEmpty();
        for (InventoryAuditService.AuditReportRow row : lastExportedReport.getRows()) {
            assertThat(row.getSku()).isNotEmpty();
            assertThat(row.getProductName()).isNotEmpty();
            assertThat(row.getDistributor()).isNotEmpty();
            assertThat(row.getErrorDetails()).isNotEmpty();
            assertThat(row.getLastImportDate()).isNotEmpty();
        }
    }

    @Then("hệ thống lưu vết nhật ký xuất báo cáo kiểm toán vào lịch sử hoạt động quản trị kho")
    public void he_thong_luu_vet_nhat_ky_xuat_bao_cao() {
        assertThat(inventoryAuditService.getAuditLogs()).isNotEmpty();
    }
}
