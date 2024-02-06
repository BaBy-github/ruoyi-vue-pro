package cn.iocoder.yudao.module.erp.controller.admin.stock;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.operatelog.core.annotations.OperateLog;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.in.ErpStockInPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.in.ErpStockInRespVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.in.ErpStockInSaveReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockInDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockInItemDO;
import cn.iocoder.yudao.module.erp.service.product.ErpProductService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockInService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.operatelog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - ERP 其它入库单")
@RestController
@RequestMapping("/erp/stock-in")
@Validated
public class ErpStockInController {

    @Resource
    private ErpStockInService stockInService;
    @Resource
    private ErpStockService stockService;
    @Resource
    private ErpProductService productService;

    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "创建其它入库单")
    @PreAuthorize("@ss.hasPermission('erp:stock-in:create')")
    public CommonResult<Long> createStockIn(@Valid @RequestBody ErpStockInSaveReqVO createReqVO) {
        return success(stockInService.createStockIn(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新其它入库单")
    @PreAuthorize("@ss.hasPermission('erp:stock-in:update')")
    public CommonResult<Boolean> updateStockIn(@Valid @RequestBody ErpStockInSaveReqVO updateReqVO) {
        stockInService.updateStockIn(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除其它入库单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('erp:stock-in:delete')")
    public CommonResult<Boolean> deleteStockIn(@RequestParam("id") Long id) {
        stockInService.deleteStockIn(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得其它入库单")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:stock-in:query')")
    public CommonResult<ErpStockInRespVO> getStockIn(@RequestParam("id") Long id) {
        ErpStockInDO stockIn = stockInService.getStockIn(id);
        if (stockIn == null) {
            return success(null);
        }
        List<ErpStockInItemDO> stockInItems = stockInService.getStockInItemListByInId(id);

        return success(BeanUtils.toBean(stockIn, ErpStockInRespVO.class, stockInVO ->
                stockInVO.setItems(BeanUtils.toBean(stockInItems, ErpStockInRespVO.Item.class, item -> {
                    ErpStockDO stock = stockService.getStock(item.getProductId(), item.getWarehouseId());
                    item.setStockCount(stock != null ? stock.getCount() : BigDecimal.ZERO);
                }))));
    }

    @GetMapping("/page")
    @Operation(summary = "获得其它入库单分页")
    @PreAuthorize("@ss.hasPermission('erp:stock-in:query')")
    public CommonResult<PageResult<ErpStockInRespVO>> getStockInPage(@Valid ErpStockInPageReqVO pageReqVO) {
        PageResult<ErpStockInDO> pageResult = stockInService.getStockInPage(pageReqVO);
        return success(buildStockInVOPageResult(pageResult));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出其它入库单 Excel")
    @PreAuthorize("@ss.hasPermission('erp:stock-in:export')")
    @OperateLog(type = EXPORT)
    public void exportStockInExcel(@Valid ErpStockInPageReqVO pageReqVO,
              HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ErpStockInRespVO> list = buildStockInVOPageResult(stockInService.getStockInPage(pageReqVO)).getList();
        // 导出 Excel
        ExcelUtils.write(response, "其它入库单.xls", "数据", ErpStockInRespVO.class, list);
    }

    private PageResult<ErpStockInRespVO> buildStockInVOPageResult(PageResult<ErpStockInDO> pageResult) {
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        // 1.1 入库项
        List<ErpStockInItemDO> stockInItemList = stockInService.getStockInItemListByInIds(
                convertSet(pageResult.getList(), ErpStockInDO::getId));
        Map<Long, List<ErpStockInItemDO>> stockInItemMap = convertMultiMap(stockInItemList, ErpStockInItemDO::getInId);
        // 1.2 商品信息
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                convertSet(stockInItemList, ErpStockInItemDO::getProductId));
        // 1.3 管理员信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(pageResult.getList(), erpStockRecordDO -> Long.parseLong(erpStockRecordDO.getCreator())));
        // 2. 开始拼接
        return BeanUtils.toBean(pageResult, ErpStockInRespVO.class, stockIn -> {
            stockIn.setItems(BeanUtils.toBean(stockInItemMap.get(stockIn.getId()), ErpStockInRespVO.Item.class,
                    item -> MapUtils.findAndThen(productMap, item.getProductId(),
                            product -> item.setProductName(product.getName()).setProductUnitName(product.getUnitName()))));
            stockIn.setProductNames(CollUtil.join(stockIn.getItems(), "，", ErpStockInRespVO.Item::getProductName));
            MapUtils.findAndThen(userMap, Long.parseLong(stockIn.getCreator()), user -> stockIn.setCreatorName(user.getNickname()));
        });
    }

}