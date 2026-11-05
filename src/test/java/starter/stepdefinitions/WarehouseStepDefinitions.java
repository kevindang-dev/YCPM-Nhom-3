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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Given("Quản trị viên đã đăng nhập vào hệ thống quản trị kho BeautyCare")
    public void quan_tri_vien_da_dang_nhap() {
        assertThat(publishingGatekeeper).isNotNull();
        assertThat(productRepository).isNotNull();
    }

    @Given("Quản trị viên danh mục & kho hàng đã đăng nhập vào hệ thống quản trị kho BeautyCare")
    public void quan_tri_vien_danh_muc_da_dang_nhap() {
        quan_tri_vien_da_dang_nhap();
    }

    @Given("hệ thống đã thiết lập danh mục hoạt chất nhạy cảm cảnh báo gồm:")
    public void he_thong_da_thiet_lap_danh_muc_hoat_chat_nhay_cam(DataTable dataTable) {
        hazardDictionary.clear();
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> row : rows) {
            String category = row.get("Nhom_Chat");
            String keywords = row.get("Tu_Khoa_Nhan_Dien");
            String flagName = row.get("Co_Canh_Bao_Kich_Hoat");
            WarningFlag flag = WarningFlag.of(flagName);
            if (keywords != null) {
                String[] parts = keywords.split("[,;]+");
                for (String kw : parts) {
                    String cleanKw = kw.trim();
                    if (!cleanKw.isEmpty()) {
                        hazardDictionary.addEntry(category, cleanKw, "", flag);
                    }
                }
            }
        }
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
    // USER STORY 1: STOCK RELEASE & PUBLISHING GATEKEEPER
    // =========================================================================================

    @Given("sản phẩm {string} trong kho đang ở trạng thái {string}")
    public void san_pham_trong_kho_dang_o_trang_thai(String productName, String statusName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.setStatus(ProductStatus.fromVietnamese(statusName));
        productRepository.save(currentProduct);
    }

    @Given("sản phẩm {string} đang ở trạng thái {string}")
    public void san_pham_dang_o_trang_thai(String productName, String statusName) {
        san_pham_trong_kho_dang_o_trang_thai(productName, statusName);
    }

    @Given("sản phẩm {string} trong kho đang ở trạng thái {string} với nhãn {string}")
    public void san_pham_trong_kho_dang_o_trang_thai_voi_nhan(String productName, String statusName, String skinTag) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.setStatus(ProductStatus.fromVietnamese(statusName));
        currentProduct.addSkinType(SkinTypeTag.of(skinTag));
        productRepository.save(currentProduct);
    }

    @Given("^sản phẩm \"([^\"]+)\" đã được gắn nhãn loại da gồm (.+)$")
    public void san_pham_da_duoc_gan_nhan_loai_da_gom_voi_ten(String productName, String tagsString) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(tagsString);
        while (matcher.find()) {
            currentProduct.addSkinType(SkinTypeTag.of(matcher.group(1)));
        }
        productRepository.save(currentProduct);
    }

    @Given("^sản phẩm đã được gắn nhãn loại da gồm (.+)$")
    public void san_pham_da_duoc_gan_nhan_loai_da_gom(String tagsString) {
        assertThat(currentProduct).isNotNull();
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(tagsString);
        while (matcher.find()) {
            currentProduct.addSkinType(SkinTypeTag.of(matcher.group(1)));
        }
        productRepository.save(currentProduct);
    }

    @Given("sản phẩm đã được gắn các nhãn loại da:")
    public void san_pham_da_duoc_gan_cac_nhan_loai_da(DataTable dataTable) {
        assertThat(currentProduct).isNotNull();
        List<String> tags = dataTable.asList();
        for (String tag : tags) {
            if (!"Nhãn loại da".equalsIgnoreCase(tag.trim())) {
                currentProduct.addSkinType(SkinTypeTag.of(tag));
            }
        }
        productRepository.save(currentProduct);
    }

    @Given("sản phẩm {string} chưa được thiết lập bất kỳ nhãn loại da nào")
    public void san_pham_chua_duoc_thiet_lap_bat_ky_nhan_loai_da_nao_voi_ten(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.clearSkinTypes();
        productRepository.save(currentProduct);
    }

    @Given("sản phẩm chưa được thiết lập bất kỳ nhãn loại da nào")
    public void san_pham_chua_duoc_thiet_lap_bat_ky_nhan_loai_da_nao() {
        assertThat(currentProduct).isNotNull();
        currentProduct.clearSkinTypes();
        productRepository.save(currentProduct);
    }

    @When("Quản trị viên thực hiện xuất bản sản phẩm {string}")
    public void quan_tri_vien_thuc_hien_xuat_ban_san_pham(String productName) {
        lastPublishResponse = publishingGatekeeper.requestPublish(productName);
        if (lastPublishResponse.getProduct() != null) {
            currentProduct = lastPublishResponse.getProduct();
        }
    }

    @When("Quản trị viên yêu cầu xuất bản sản phẩm {string}")
    public void quan_tri_vien_yeu_cau_xuat_ban_san_pham(String productName) {
        quan_tri_vien_thuc_hien_xuat_ban_san_pham(productName);
    }

    @When("Quản trị viên cố gắng xuất bản sản phẩm {string}")
    public void quan_tri_vien_co_gang_xuat_ban_san_pham(String productName) {
        quan_tri_vien_thuc_hien_xuat_ban_san_pham(productName);
    }

    @Then("trạng thái mở bán của sản phẩm {string} phải là {string}")
    public void trang_thai_mo_ban_cua_san_pham_phai_la_voi_ten(String productName, String expectedStatus) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("trạng thái mở bán của sản phẩm phải là {string}")
    public void trang_thai_mo_ban_cua_san_pham_phai_la(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("hệ thống đưa ra thông báo: {string}")
    public void he_thong_dua_ra_thong_bao(String expectedMessage) {
        assertThat(lastPublishResponse).isNotNull();
        assertThat(lastPublishResponse.getMessage()).contains(expectedMessage);
    }

    @Then("sản phẩm {string} xuất hiện trong danh sách hiển thị của bộ lọc {string} ngoài website")
    public void san_pham_xuat_hien_trong_danh_sach_hien_thi_cua_bo_loc_voi_ten(String productName, String filterCriterion) {
        List<Product> products = publishingGatekeeper.searchProductsBySkinType(SkinTypeTag.of(filterCriterion));
        List<String> names = products.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(names).contains(productName);
    }

    @Then("sản phẩm xuất hiện trong danh sách hiển thị của bộ lọc {string} ngoài website")
    public void san_pham_xuat_hien_trong_danh_sach_hien_thi_cua_bo_loc(String filterCriterion) {
        List<Product> products = publishingGatekeeper.searchProductsBySkinType(SkinTypeTag.of(filterCriterion));
        List<String> names = products.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(names).contains(currentProduct.getName());
    }

    @When("khách hàng sử dụng bộ lọc tìm kiếm theo tiêu chí {string}")
    public void khach_hang_su_dung_bo_loc_tim_kiem_theo_tieu_chi(String filterCriterion) {
        searchResults = publishingGatekeeper.searchProductsBySkinType(SkinTypeTag.of(filterCriterion));
    }

    @When("^khách hàng tìm kiếm sản phẩm theo tiêu chí phân loại da \"?([^\"]+)\"?$")
    public void khach_hang_tim_kiem_san_pham_theo_tieu_chi(String filterCriterion) {
        khach_hang_su_dung_bo_loc_tim_kiem_theo_tieu_chi(filterCriterion);
    }

    @Then("sản phẩm {string} phải hiển thị trong kết quả là {string}")
    public void san_pham_phai_hien_thi_trong_ket_qua_la(String productName, String displayStatus) {
        List<String> resultNames = searchResults.stream().map(Product::getName).collect(Collectors.toList());
        if ("Có xuất hiện".equalsIgnoreCase(displayStatus.trim())) {
            assertThat(resultNames).contains(productName);
        } else {
            assertThat(resultNames).doesNotContain(productName);
        }
    }

    @Then("hệ thống từ chối cấp phép mở bán cho sản phẩm")
    public void he_thong_tu_choi_cap_phep_mo_ban_cho_san_pham() {
        assertThat(lastPublishResponse).isNotNull();
        assertThat(lastPublishResponse.isSuccess()).isFalse();
    }

    @Then("hệ thống từ chối cấp phép mở bán")
    public void he_thong_tu_choi_cap_phep_mo_ban() {
        he_thong_tu_choi_cap_phep_mo_ban_cho_san_pham();
    }

    @Then("trạng thái của sản phẩm {string} vẫn được giữ nguyên là {string}")
    public void trang_thai_cua_san_pham_van_duoc_giu_nguyen_la_voi_ten(String productName, String expectedStatus) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("trạng thái của sản phẩm vẫn được giữ nguyên là {string}")
    public void trang_thai_cua_san_pham_van_duoc_giu_nguyen_la(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("trạng thái của sản phẩm vẫn giữ nguyên là {string}")
    public void trang_thai_cua_san_pham_van_giu_nguyen_la_alt(String expectedStatus) {
        trang_thai_cua_san_pham_van_duoc_giu_nguyen_la(expectedStatus);
    }

    @Then("hệ thống hiển thị cảnh báo: {string}")
    public void he_thong_hien_thi_canh_bao(String expectedWarning) {
        assertThat(lastPublishResponse.getMessage()).isEqualTo(expectedWarning);
    }

    @Then("sản phẩm {string} không được xuất hiện trên thanh tìm kiếm ngoài website")
    public void san_pham_khong_duoc_xuat_hien_tren_thanh_tim_kiem_voi_ten(String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.isVisibleOnStorefront()).isFalse();
    }

    @Then("sản phẩm không được xuất hiện trên thanh tìm kiếm ngoài website")
    public void san_pham_khong_duoc_xuat_hien_tren_thanh_tim_kiem() {
        assertThat(currentProduct.isVisibleOnStorefront()).isFalse();
    }

    @When("Quản trị viên cập nhật nhãn loại da của sản phẩm {string} thành danh sách rỗng")
    public void quan_tri_vien_cap_nhat_nhan_loai_da_thanh_danh_sach_rong(String productName) {
        currentProduct = productRepository.findByName(productName).orElseThrow(
                () -> new AssertionError("Không tìm thấy sản phẩm " + productName)
        );
        currentProduct.clearSkinTypes();
        publishingGatekeeper.handleProductSkinTypesRemoved(currentProduct);
    }

    @Then("hệ thống từ chối lưu thay đổi rỗng")
    public void he_thong_tu_choi_luu_thay_doi_rong() {
        assertThat(currentProduct.getSkinTypes()).isEmpty();
    }

    @Then("tự động thu hồi quyền mở bán, chuyển trạng thái sản phẩm {string} thành {string}")
    public void tu_dong_thu_hoi_quyen_mo_ban_chuyen_trang_thai_voi_ten(String productName, String expectedStatus) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("tự động thu hồi quyền mở bán, chuyển trạng thái sản phẩm thành {string}")
    public void tu_dong_thu_hoi_quyen_mo_ban_chuyen_trang_thai(String expectedStatus) {
        assertThat(currentProduct.getStatus().getVietnameseName()).isEqualTo(expectedStatus);
    }

    @Then("sản phẩm {string} lập tức biến mất khỏi danh mục hiển thị ngoài website")
    public void san_pham_lap_tuc_bien_mat_khoi_danh_muc_voi_ten(String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.isVisibleOnStorefront()).isFalse();
    }

    @Then("sản phẩm lập tức biến mất khỏi danh mục hiển thị ngoài website")
    public void san_pham_lap_tuc_bien_mat_khoi_danh_muc() {
        assertThat(currentProduct.isVisibleOnStorefront()).isFalse();
    }

    // =========================================================================================
    // USER STORY 2: AUTOMATED INCI PARSER & HAZARD INSPECTION
    // =========================================================================================

    @Given("Quản trị viên nhập chuỗi thành phần cho sản phẩm {string}:")
    public void quan_tri_vien_nhap_chuoi_thanh_phan_cho_san_pham(String productName, String docString) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            return productRepository.save(p);
        });
        currentProduct.setRawInci(docString);
        productRepository.save(currentProduct);
    }

    @When("hệ thống tiến hành thẩm định thành phần cho sản phẩm {string}")
    public void he_thong_tien_hanh_tham_dinh_thanh_phan_cho_san_pham(String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        lastParseResult = inciParserService.parseAndApplyToProduct(p, p.getRawInci());
        productRepository.save(p);
        currentProduct = p;
    }

    @Then("hệ thống bóc tách thành công {int} hoạt chất riêng biệt cho sản phẩm {string}")
    public void he_thong_boc_tach_thanh_cong_hoat_chat_rieng_biet_voi_ten(int count, String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(lastParseResult).isNotNull();
        assertThat(lastParseResult.getIngredients()).hasSize(count);
    }

    @Then("hệ thống bóc tách thành công {int} hoạt chất riêng biệt")
    public void he_thong_boc_tach_thanh_cong_hoat_chat_rieng_biet(int count) {
        assertThat(lastParseResult).isNotNull();
        assertThat(lastParseResult.getIngredients()).hasSize(count);
    }

    @Then("kích hoạt cờ cảnh báo {string} cho sản phẩm {string}")
    public void kich_hoat_co_canh_bao_cho_san_pham(String flagName, String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p.hasWarningFlag(WarningFlag.of(flagName))).isTrue();
    }

    @Then("hệ thống kích hoạt đồng thời {int} cờ cảnh báo gồm {string} và {string} cho sản phẩm {string}")
    public void he_thong_kich_hoat_dong_thoi_co_canh_bao_voi_ten(int count, String flag1, String flag2, String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(lastParseResult.getDetectedHazards()).hasSize(count);
        assertThat(lastParseResult.getDetectedHazards()).containsKey(WarningFlag.of(flag1));
        assertThat(lastParseResult.getDetectedHazards()).containsKey(WarningFlag.of(flag2));
        assertThat(p.hasWarningFlag(WarningFlag.of(flag1))).isTrue();
        assertThat(p.hasWarningFlag(WarningFlag.of(flag2))).isTrue();
    }

    @Then("hệ thống kích hoạt đồng thời {int} cờ cảnh báo gồm {string} và {string}")
    public void he_thong_kich_hoat_dong_thoi_co_canh_bao(int count, String flag1, String flag2) {
        assertThat(lastParseResult.getDetectedHazards()).hasSize(count);
        assertThat(lastParseResult.getDetectedHazards()).containsKey(WarningFlag.of(flag1));
        assertThat(lastParseResult.getDetectedHazards()).containsKey(WarningFlag.of(flag2));
        assertThat(currentProduct.hasWarningFlag(WarningFlag.of(flag1))).isTrue();
        assertThat(currentProduct.hasWarningFlag(WarningFlag.of(flag2))).isTrue();
    }

    @Then("hệ thống xác nhận sản phẩm {string} đạt trạng thái an toàn {string}")
    public void he_thong_xac_nhan_san_pham_dat_trang_thai_an_toan_voi_ten(String productName, String safetyState) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(lastParseResult.isAlcoholFree()).isTrue();
        assertThat(lastParseResult.getDetectedSafeFattyAlcohols()).isNotEmpty();
        p.addSafetyLabel(safetyState);
        productRepository.save(p);
    }

    @Then("hệ thống xác nhận sản phẩm đạt trạng thái an toàn {string}")
    public void he_thong_xac_nhan_san_pham_dat_trang_thai_an_toan(String safetyState) {
        assertThat(lastParseResult.isAlcoholFree()).isTrue();
        assertThat(lastParseResult.getDetectedSafeFattyAlcohols()).isNotEmpty();
        currentProduct.addSafetyLabel(safetyState);
        productRepository.save(currentProduct);
    }

    @Then("tuyệt đối không kích hoạt cờ cảnh báo {string} cho sản phẩm {string}")
    public void tuyet_doi_khong_kich_hoat_co_canh_bao_voi_ten(String flagName, String productName) {
        Product p = productRepository.findByName(productName).orElse(currentProduct);
        assertThat(p).isNotNull();
        assertThat(p.hasWarningFlag(WarningFlag.of(flagName))).isFalse();
    }

    @Then("tuyệt đối không kích hoạt cờ cảnh báo {string}")
    public void tuyet_doi_khong_kich_hoat_co_canh_bao(String flagName) {
        assertThat(currentProduct.hasWarningFlag(WarningFlag.of(flagName))).isFalse();
    }

    @Then("hệ thống nhận diện hoạt chất {string} tương đương với cồn khô")
    public void he_thong_nhan_dien_hoat_chat_tuong_duong_voi_con_kho(String aliasName) {
        Optional<HazardDictionary.HazardEntry> hazardOpt = inciParserService.findHazard(aliasName);
        assertThat(hazardOpt).isPresent();
        assertThat(hazardOpt.get().getCategory()).isEqualTo("Cồn khô");
    }

    // =========================================================================================
    // USER STORY 3: INVENTORY DATA AUDIT DASHBOARD
    // =========================================================================================

    @Given("cơ sở dữ liệu kho đang lưu trữ {int} sản phẩm gồm:")
    public void co_so_du_lieu_kho_dang_luu_tru_san_pham_gom(int count, DataTable dataTable) {
        productRepository.clear();
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> row : rows) {
            String name = row.get("Ten_San_Pham");
            String skinType = row.get("Nhan_Loai_Da");
            String status = row.get("Trang_Thai");

            Product p = new Product(name);
            if (skinType != null && !skinType.trim().isEmpty()) {
                p.addSkinType(SkinTypeTag.of(skinType));
            } else {
                p.setAuditError("Chưa phân loại da");
            }
            if (status != null && !status.trim().isEmpty()) {
                p.setStatus(ProductStatus.fromVietnamese(status));
            }
            productRepository.save(p);
        }
    }

    @When("Quản trị viên thực hiện lọc danh sách theo điều kiện {string}")
    public void quan_tri_vien_thuc_hien_loc_danh_sach_theo_dieu_kien(String criterion) {
        filteredAuditProducts = inventoryAuditService.filterByAuditCriterion(criterion);
    }

    @Then("hệ thống trả về kết quả gồm đúng {int} sản phẩm cần xử lý")
    public void he_thong_tra_ve_ket_qua_gom_dung_san_pham_can_xu_ly(int expectedCount) {
        assertThat(filteredAuditProducts).hasSize(expectedCount);
    }

    @Then("danh sách kết quả chứa sản phẩm {string} và {string}")
    public void danh_sach_ket_qua_chua_san_pham_va(String p1, String p2) {
        List<String> names = filteredAuditProducts.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(names).contains(p1, p2);
    }

    @Given("sản phẩm {string} đang nằm trong danh sách cảnh báo thiếu nhãn da")
    public void san_pham_dang_nam_trong_danh_sach_canh_bao_thieu_nhan_da(String productName) {
        currentProduct = productRepository.findByName(productName).orElseGet(() -> {
            Product p = new Product(productName);
            p.clearSkinTypes();
            p.setAuditError("Chưa phân loại da");
            return productRepository.save(p);
        });
    }

    @When("Quản trị viên thực hiện cập nhật nhanh nhãn loại da {string} cho sản phẩm {string}")
    public void quan_tri_vien_thuc_hien_cap_nhat_nhanh_nhan_loai_da(String skinTag, String productName) {
        inventoryAuditService.quickEditSkinTypes(productName, Collections.singleton(SkinTypeTag.of(skinTag)));
    }

    @Then("sản phẩm {string} tự động biến mất khỏi danh sách cảnh báo thiếu nhãn da")
    public void san_pham_tu_dong_bien_mat_khoi_danh_sach_canh_bao(String productName) {
        List<Product> violations = inventoryAuditService.filterByAuditCriterion("Chưa phân loại da");
        List<String> names = violations.stream().map(Product::getName).collect(Collectors.toList());
        assertThat(names).doesNotContain(productName);
    }

    @Then("nhãn loại da của sản phẩm {string} trong kho được ghi nhận là {string}")
    public void nhan_loai_da_cua_san_pham_trong_kho_duoc_ghi_nhan_la(String productName, String expectedTag) {
        Product p = productRepository.findByName(productName).orElseThrow(
                () -> new AssertionError("Không tìm thấy sản phẩm " + productName)
        );
        assertThat(p.getSkinTypes()).contains(SkinTypeTag.of(expectedTag));
    }

    @Given("có ít nhất một sản phẩm trong kho đang bị thiếu nhãn loại da")
    public void co_it_nhat_mot_san_pham_trong_kho_dang_bi_thieu_nhan_loai_da() {
        if (productRepository.findUnclassifiedSkinType().isEmpty()) {
            Product p = new Product("SKU-999", "Serum Test Lỗi", "NPP BeautyCare", "Chưa phân loại da", "2026-10-30");
            p.clearSkinTypes();
            productRepository.save(p);
        }
    }

    @When("Quản trị viên yêu cầu kết xuất báo cáo kiểm toán dữ liệu kho")
    public void quan_tri_vien_yeu_cau_ket_xuat_bao_cao_kiem_toan_du_lieu_kho() {
        lastExportedReport = inventoryAuditService.exportAuditReport("XLSX");
    }

    @Then("hệ thống tạo ra tệp báo cáo dạng bảng tính với định dạng {string}")
    public void he_thong_tao_ra_tep_bao_cao_dang_bang_tinh(String expectedFormat) {
        assertThat(lastExportedReport).isNotNull();
        assertThat(lastExportedReport.getFormat()).isEqualToIgnoringCase(expectedFormat);
    }

    @Then("nội dung báo cáo thống kê đầy đủ mã sản phẩm và lý do lỗi dữ liệu")
    public void noi_dung_bao_cao_thong_ke_day_du_ma_san_pham_va_ly_do() {
        assertThat(lastExportedReport.getRows()).isNotEmpty();
        for (InventoryAuditService.AuditReportRow row : lastExportedReport.getRows()) {
            assertThat(row.getSku()).isNotEmpty();
            assertThat(row.getErrorDetails()).isNotEmpty();
        }
    }
}
