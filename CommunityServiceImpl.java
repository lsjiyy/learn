package com.xinge.yijia.server.web.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xinge.yijia.server.common.business.*;
import com.xinge.yijia.server.common.expression.Expression;
import com.xinge.yijia.server.common.pojo.*;
import com.xinge.yijia.server.common.dao.*;
import com.xinge.yijia.server.common.message.*;
import com.xinge.yijia.server.common.utils.ExcelUtil;
import com.xinge.yijia.server.common.utils.TimestampUtils;

import com.xinge.yijia.server.common.message.request.*;
import com.xinge.yijia.server.common.message.response.*;
import com.xinge.yijia.server.web.service.common.IFileService;
import com.xinge.yijia.server.web.strore.IRulePicturePathStore;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

/**
 * @Auther: lsj
 * @Date: Created in 10:00 2019/3/19
 * @Description:
 */
@Service
public class CommunityServiceImpl implements ICommunityService {
    private static final Logger logger = LoggerFactory.getLogger(CommunityServiceImpl.class);

    @Autowired
    private ISystemService systemService;
    @Autowired
    private ICommunityInfoDao communityInfoDao;
    @Autowired
    private IRulePicturePathStore rulePicturePathStore;
    @Autowired
    private IFileService fileService;
    @Autowired
    private IBuildingInfoDao buildingInfoDao;
    @Autowired
    private IUnitInfoDao unitInfoDao;
    @Autowired
    private IEstateInfoDao estateInfoDao;
    @Autowired
    private IRoomInfoDao roomInfoDao;
    @Autowired
    private IStaffService staffService;
    @Autowired
    private IStaffCommunityRelationDao staffRelationDao;
    @Autowired
    private IOwnerService ownerService;
    @Autowired
    private IChargeService chargeService;
    @Autowired
    private ILabelService labelService;
    @Autowired
    private ICarportService carportService;
    @Autowired
    private ICarService carService;

    /**
     * 创建小区
     *
     * @param staffId
     * @param request
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult createCommunity(long staffId, CommunityRequest request) {
        logger.info("CREATE COMMUNITY REQUEST ===>{},staffId ===>{}", request.toString(), staffId);
        //todo  做操作记录
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("创建了").append(request.getCommunityName()).append("小区");
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        BusinessResult businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        CommunityInfo communityInfo = new CommunityInfo();
        communityInfo.setAddress(request.getAddress());
        communityInfo.setCommunityName(request.getCommunityName());
        communityInfo.setCompanyId(staffInfo.getCompanyId());  //物业公司Id
        communityInfo.setConcatPhone(request.getConcatPhone());
        communityInfo.setConcatName(request.getConcatName());
        communityInfo.setLocation(request.getLocation());
        communityInfo.setServicePhone(request.getServicePhone());
        communityInfo.setDevelopers(request.getDevelopers());
        communityInfoDao.insert(communityInfo);
        if (request.getLogo() != null) {
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("communityId", communityInfo.getCommunityId());
            String logo = fileService.uploadPicture(request.getLogo(), PathType.COMMUNITY_LOGO, variableMap);
            communityInfoDao.updateLogo(communityInfo.getCommunityId(), logo);
        }
        return businessResult;
    }

    /**
     * 导入房屋
     * todo 初步完成
     *
     * @param request
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult importRoom(ImportFileRequest request) throws Exception {
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(request.getCommunityId());
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("导入了").append(communityInfo.getCommunityName()).append("小区的房屋列表");
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        BusinessResult businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        XSSFWorkbook workbook = new XSSFWorkbook(request.getFile().getInputStream());//创建Excel表格读取对象
        XSSFSheet sheet = workbook.getSheetAt(0);//返回单元格第一个元素
        HashSet<BuildingUnit> buildingUnitHashSet = getBuildingUnit(sheet, communityInfo.getCommunityId());
        //获取第一个标签页
        for (Row rows : sheet) {
            if (rows.getRowNum() != 0) { //略过title
                if (rows != null) {
                    RoomInfo roomInfo = getRoomInfo(rows, buildingUnitHashSet);
                    if (request.getStatus() == 1) { //重复数据不导入
                        boolean exist = roomInfoDao.selectExist(roomInfo.getRoomName(), roomInfo.getBuildingId(), roomInfo.getUnitId());
                        if (exist)  //重复不添加
                            continue;
                    }

                    //房屋基本信息
                    String deliveryTime = rows.getCell(9).getStringCellValue(); //交房时间
                    if (!StringUtils.isEmpty(deliveryTime))
                        roomInfo.setDeliveryTime(TimestampUtils.parse(deliveryTime, TimestampUtils.YYYY_MM_DD));

                    String reservedPhone = rows.getCell(12).getStringCellValue();
                    String reservedName = rows.getCell(13).getStringCellValue();
                    String roomStatus = rows.getCell(15).getStringCellValue();
                    int status;
                    if (roomStatus.equals(EstateStatus.LIVING.getStatusExplain())) {
                        status = EstateStatus.LIVING.getStatus();
                    } else if (roomStatus.equals(EstateStatus.UN_LIVING.getStatusExplain())) {
                        status = EstateStatus.UN_LIVING.getStatus();  //未交房  有住户  空置
                    } else {
                        status = EstateStatus.FOR_SALE.getStatus(); //未有住户 未售
                    }
                    EstateInfo estateInfo = createEstateInfo(EstateType.ROOM, communityInfo.getCommunityId(), status);
                    roomInfo.setEstateId(estateInfo.getEstateId());
                    roomInfo.setReservedPhone(reservedPhone); //预留手机号
                    roomInfoDao.insert(roomInfo);

                    if (!StringUtils.isEmpty(reservedPhone)) {
                        String[] phoneArr = reservedPhone.split(",");
                        if (!StringUtils.isEmpty(reservedName)) {
                            String[] nameArr = reservedName.split(",");
                            for (int i = 0; i < phoneArr.length; i++) {
                                OwnerInfo ownerInfo = new OwnerInfo();
                                ownerInfo.setOwnerPhone(phoneArr[i]);
                                ownerInfo.setOwnerName(nameArr[i]);
                                ownerInfo.setSex(1);
                                ownerInfo.setCommunityId(request.getCommunityId());
                                ownerService.ownerEstateRelation(estateInfo, ownerInfo);
                            }
                        }
                    }
                }
            }
        }
        return businessResult;
    }

    /**
     * 后台获取小区列表
     *
     * @param request
     * @return
     */
    @Override
    public PageResult<CommunityInfoItem> staffGetCommunity(SearchCommunityRequest request) {
        logger.info("request ===>{}", request.toString());
        List<CommunityInfoItem> infoItemList = new ArrayList<>();
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        Page page;
        if (staffInfo.getRoles().contains(String.valueOf(StaffRole.ALL_RIGHT.getRoleId())) || staffInfo.getRoles().contains(String.valueOf(StaffRole.ADMIN.getRoleId()))) { //超管获取小区列表
            page = PageHelper.startPage(request.getPage(), request.getCurrentCount(), true, false, null);
            List<CommunityInfo> communityInfoList = communityInfoDao.selectByCompanyId(request.getCommunityName(), staffInfo.getCompanyId());
            for (CommunityInfo communityInfo : communityInfoList) {
                CommunityInfoItem infoItem = new CommunityInfoItem(communityInfo);
                infoItemList.add(infoItem);
            }
        } else {
            page = PageHelper.startPage(request.getPage(), request.getCurrentCount(), true, false, null);
            infoItemList = communityInfoDao.selectByStaffParm(request.getStaffId(), request.getCommunityName());
        }
        PageResult<CommunityInfoItem> result = new PageResult<>(infoItemList, page);
        return result;
    }

    /**
     * 编辑小区
     *
     * @param: communityRequest
     * @auther: lucy
     * @date:
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult staffSetCommunity(long staffId, long communityId, CommunityRequest request) {
        logger.info("request ===>{},staffId ===>{},communityId ===>{}", request.toString(), staffId, communityId);
        BusinessResult businessResult = new BusinessResult();
        StringBuilder textBuilder = new StringBuilder("");
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(communityId);
        if (communityInfo == null) {
            businessResult.setBusinessCode(ResultCode.COMMUNITY_NOT_FOUND);
            return businessResult;
        }
        textBuilder.append("修改了小区-").append(communityInfo.getCommunityName()).append("的信息");
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        if (request.getLogo() != null) {
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("communityId", communityInfo.getCommunityId());
            String logo = fileService.uploadPicture(request.getLogo(), PathType.COMMUNITY_LOGO, variableMap);
            communityInfoDao.updateLogo(communityInfo.getCommunityId(), logo);
        }
        communityInfo.setCommunityName(request.getCommunityName());
        communityInfo.setConcatName(request.getConcatName());
        communityInfo.setConcatPhone(request.getConcatPhone());
        communityInfo.setAddress(request.getAddress());
        communityInfo.setServicePhone(request.getServicePhone());
        communityInfo.setConcatName(request.getConcatName());
        communityInfo.setLocation(request.getLocation());
        communityInfo.setDevelopers(request.getDevelopers());
        communityInfoDao.updateCommunity(communityInfo);
        businessResult.setBusinessCode(ResultCode.OK);
        return businessResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult createBuilding(CreateBuildingRequest request) {
        BusinessResult businessResult = new BusinessResult();
        logger.info("CREATE BUILDING REQUEST ===>{}", request.toString());
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(request.getCommunityId());
        StringBuilder textBuilder = new StringBuilder();
        if (request.getUnitCount() > 20) {
            businessResult.setBusinessCode(ResultCode.UNIT_UPPER);
            return businessResult;
        }
        textBuilder.append("创建了").append(communityInfo.getCommunityName()).append("小区楼栋-").append(request.getBuildingName());

        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingName(request.getCommunityId(), request.getBuildingName());
        if (buildingInfo != null) {
            businessResult.setBusinessCode(ResultCode.BUILDING_EXIST);
            return businessResult;
        }
        buildingInfo = new BuildingInfo();
        buildingInfo.setBuildingName(request.getBuildingName());
        buildingInfo.setBuildingType(request.getBuildingType());
        buildingInfo.setRemark(request.getRemark());
        buildingInfo.setDirection(request.getDirection());
        buildingInfo.setFloors(request.getFloors());
        buildingInfo.setDirection(request.getDirection());
        buildingInfo.setCommunityId(request.getCommunityId());
        buildingInfo.setStructure(request.getStructure());
        buildingInfoDao.insert(buildingInfo);
        //单元创建
        for (int i = 1; i <= request.getUnitCount(); i++) {
            UnitInfo unitInfo = new UnitInfo();
            unitInfo.setUnitName(i + "单元");
            unitInfo.setBuildingId(buildingInfo.getBuildingId());
            unitInfoDao.insert(unitInfo);
        }
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;

        return businessResult;
    }

    @Override
    public BusinessResult createUnit(String unitName, long buildingId, long staffId) {
        BusinessResult businessResult = new BusinessResult();
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(buildingId);
        if (buildingInfo == null) {
            businessResult.setBusinessCode(ResultCode.BUILDING_NOT_FOUND);
            return businessResult;
        }
        UnitInfo unitInfo = unitInfoDao.selectByUnitName(buildingId, unitName);
        if (unitInfo != null) {
            businessResult.setBusinessCode(ResultCode.UNIT_EXIST);
            return businessResult;
        }
        List<UnitInfo> unitInfoList = unitInfoDao.selectByBuildingId(buildingId);
        if (unitInfoList.size() >= 20) {
            businessResult.setBusinessCode(ResultCode.UNIT_UPPER);
            return businessResult;
        }
        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append("创建了").append(buildingInfo.getBuildingName()).append("的单元-").append(unitName);
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        unitInfo = new UnitInfo();
        unitInfo.setUnitName(unitName);
        unitInfo.setBuildingId(buildingId);
        unitInfoDao.insert(unitInfo);
        return businessResult;
    }

    /**
     * 获取楼宇信息
     *
     * @param request
     * @return
     */
    @Override
    public PageResult<BuildingInfoItem> getBuildingList(SearchBuildingRequest request) {
        List<BuildingInfoItem> infoItemList;
        Map<Long, String> communityMap = new HashMap<>();
        if (StringUtils.isEmpty(request.getCommunityId())) { //无小区Id  默认员工下所有小区
            List<CommunityInfo> communityInfoList = staffGetCommunity(request.getStaffId(), null);
            if (communityInfoList.isEmpty())
                return null;
            for (CommunityInfo communityInfo : communityInfoList) {  //标签,楼栋,名称
                communityMap.put(communityInfo.getCommunityId(), communityInfo.getCommunityName());
            }
            PageHelper.startPage(request.getPage(), request.getCurrentCount(), true, false, null);
            infoItemList = buildingInfoDao.selectByCommunityIdList(communityMap.keySet(), request.getLabelId(), request.getBuildingName());
        } else { //根据小区Id筛选
            PageHelper.startPage(request.getPage(), request.getCurrentCount(), true, false, null);
            infoItemList = buildingInfoDao.selectBuildingItem(Long.valueOf(request.getCommunityId()), request.getBuildingName(), request.getLabelId());

        }
       /* for (BuildingInfo buildingInfo : buildingInfoList) {
            BuildingInfoItem infoItem = new BuildingInfoItem(buildingInfo);
            String communityName = communityMap.get(buildingInfo.getCommunityId());
            List<UnitInfo> unitInfoList = unitInfoDao.selectByBuildingId(buildingInfo.getBuildingId());
            infoItem.setCommunityName(communityName);
            infoItem.setUnitCount(unitInfoList.size());
            infoItemList.add(infoItem);
        }*/
        PageResult<BuildingInfoItem> result = new PageResult<>(infoItemList);
        return result;
    }

    /**
     * 员工获取小区列表
     *
     * @param staffId
     * @param communityName
     * @return
     */
    @Override
    public List<CommunityInfo> staffGetCommunity(long staffId, String communityName) {
        List<CommunityInfo> communityInfoList = new ArrayList<>();
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        if (staffInfo == null)
            return communityInfoList;
        if (!StringUtils.isEmpty(staffInfo.getRoles()) && (staffInfo.getRoles().equals(String.valueOf(StaffRole.ADMIN.getRoleId())) || staffInfo.getRoles().equals(String.valueOf(StaffRole.ALL_RIGHT.getRoleId())))) {
            communityInfoList = communityInfoDao.selectByCompanyId(communityName, staffInfo.getCompanyId());
        } else {
            communityInfoList = staffRelationDao.selectByStaffId(staffId, communityName);
        }
        return communityInfoList;
    }

    /**
     * 单个获取小区
     *
     * @param: communityId
     * @auther: lucy
     * @date:
     */
    @Override
    public CommunityInfoItem getCommunityInfo(long communityId) {
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(communityId);
        if (communityInfo == null) {
            return null;
        }
        CommunityInfoItem communityInfoItem = new CommunityInfoItem(communityInfo);
        return communityInfoItem;
    }

    /**
     * 楼栋信息
     *
     * @param buildingId
     * @return
     */
    @Override
    public BuildingInfoItem getBuildingInfo(long buildingId) {
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(buildingId);
        if (buildingInfo == null) {
            return null;
        }
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(buildingInfo.getCommunityId());
        List<UnitInfo> unitInfoList = unitInfoDao.selectByBuildingId(buildingId);
        BuildingInfoItem infoItem = new BuildingInfoItem(buildingInfo);
        infoItem.setCommunityName(communityInfo.getCommunityName());
        infoItem.setUnitCount(unitInfoList.size());
        return infoItem;
    }

    @Override
    public PageResult<UnitInfoItem> getUnitList(long buildingId, int page, int currentCount) {
        PageHelper.startPage(page, currentCount, true, false, null);
        List<UnitInfoItem> infoItemList = unitInfoDao.selectUnitItem(buildingId);
        PageResult<UnitInfoItem> result = new PageResult<>(infoItemList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult createRoom(CreateRoomRequest request) throws Exception {
        logger.info("CREATE ROOM REQUEST ===>{}", request.toString());
        BusinessResult businessResult = new BusinessResult();
        boolean flag = roomInfoDao.selectExist(request.getRoomName(), request.getBuildingId(), request.getUnitId());
        if (flag) {
            businessResult.setBusinessCode(ResultCode.ROOM_EXIST);
            return businessResult;
        }
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(request.getBuildingId());
        if (request.getFloorNum() > buildingInfo.getFloors()) {
            businessResult.setBusinessCode(ResultCode.FLOOR_UPPER);
            return businessResult;
        }

        RoomInfo roomInfo = new RoomInfo();
        if (StringUtils.isEmpty(request.getDeliveryTime()) && request.getStatus() == EstateStatus.LIVING.getStatus()) {
            businessResult.setBusinessCode(ResultCode.MESSAGE_ERROR);
            return businessResult;
        } else if (!StringUtils.isEmpty(request.getDeliveryTime())) {
            roomInfo.setDeliveryTime(TimestampUtils.parse(request.getDeliveryTime(), TimestampUtils.YYYY_MM_DD)); //交房时间
        }
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("创建了").append(buildingInfo.getBuildingName()).append("下的房屋-").append(request.getRoomName());
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;

        EstateInfo estateInfo = createEstateInfo(EstateType.ROOM, buildingInfo.getCommunityId(), request.getStatus());
        roomInfo.setEstateId(estateInfo.getEstateId());
        roomInfo.setRoomArea(request.getRoomArea());  //建筑面积
        roomInfo.setEquallyArea(request.getEquallyArea()); //公摊面积
        roomInfo.setInsideArea(request.getInsideArea()); //套内面积
        roomInfo.setFloorNum(request.getFloorNum()); //所在楼层
        roomInfo.setRemark(request.getRemark()); //备注
        roomInfo.setHouseType(request.getHouseType()); //户型
        roomInfo.setUnitId(request.getUnitId()); //单元
        roomInfo.setBuildingId(request.getBuildingId()); //楼栋
        roomInfo.setRoomType(request.getRoomType()); //房屋类型
        roomInfo.setPropertyYear(request.getPropertyYear()); //产权年限
        roomInfo.setRoomName(request.getRoomName());
        roomInfo.setDirection(request.getDirection());

        roomInfoDao.insert(roomInfo);
        if (request.getPictureList() != null) {
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("communityId", estateInfo.getCommunityId());
            variableMap.put("estateId", estateInfo.getEstateId());
            String path = fileService.uploadPictureList(request.getPictureList(), PathType.ROOM_PATH, variableMap);
            roomInfoDao.updatePicture(roomInfo.getRoomId(), path);
        }

        return businessResult;
    }


    @Override
    public EstateInfo createEstateInfo(int estateTypeId, long communityId, int status) {
        EstateInfo estateInfo = new EstateInfo();
        estateInfo.setEstateTypeId(estateTypeId);
        estateInfo.setStatus(status);
        estateInfo.setCommunityId(communityId);
        estateInfoDao.insert(estateInfo);
        return estateInfo;
    }

    /**
     * 编辑楼栋
     *
     * @param: request
     * @auther: lucy
     * return:businessResult
     */
    @Override
    public BusinessResult setBuildingRedact(UpdateBuildingRequest request) {
        logger.info("UPDATE BUILDING REQUEST ===>{}", request.toString());
        BusinessResult businessResult = new BusinessResult();
        //查看楼栋是否存在
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(request.getBuildingId());
        if (buildingInfo == null) {
            businessResult.setBusinessCode(ResultCode.BUILDING_NOT_FOUND);
            return businessResult;
        }
        //查看楼名是否重复
        if (!buildingInfo.getBuildingName().equals(request.getBuildingName())) {
            BuildingInfo building = buildingInfoDao.selectByBuildingName(buildingInfo.getCommunityId(), request.getBuildingName());
            if (building != null) {
                businessResult.setBusinessCode(ResultCode.BUILDING_EXIST);
                return businessResult;
            }
        }
        //记录日志
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(buildingInfo.getCommunityId());
        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append("修改了").append(communityInfo.getCommunityName()).append("小区楼栋-").append(request.getBuildingName());
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        //封装
        buildingInfo.setBuildingId(request.getBuildingId());
        buildingInfo.setBuildingName(request.getBuildingName());
        buildingInfo.setBuildingType(request.getBuildingType());
        buildingInfo.setFloors(request.getFloors());
        buildingInfo.setStructure(request.getStructure());
        buildingInfo.setDirection(request.getDirection());
        buildingInfo.setRemark(request.getRemark());
        buildingInfoDao.updateBuildingRedact(buildingInfo);
        businessResult.setBusinessCode(ResultCode.OK);
        return businessResult;
    }

    /**
     * 单元信息修改
     *
     * @param: staffId unitId unitName
     * @auther: lucy
     * @date: 2019-3-26 10:37:05
     */
    @Override
    public BusinessResult setUnitInfo(long staffId, long unitId, String unitName) {
        logger.info("staffId ===>{},unitId ===>{},unitName ===>{}", staffId, unitId, unitName);
        BusinessResult businessResult = new BusinessResult();
        UnitInfo unitInfo = unitInfoDao.selectByUnitId(unitId);
        //查询此单元是否存在
        if (unitInfo == null) {
            businessResult.setBusinessCode(ResultCode.UNIT_NOT_EXIST);
            return businessResult;
        }
        //查询修改的单元是否重复
        if (!unitInfo.getUnitName().equals(unitName)) {
            boolean flag = unitInfoDao.selectExist(unitInfo.getBuildingId(), unitName);
            if (flag) {
                businessResult.setBusinessCode(ResultCode.UNIT_EXIST);
                return businessResult;
            }
        }

        //操作记录
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("修改了-").append(unitInfo.getUnitName()).append("-单元名为-").append(unitName);
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        unitInfo.setUnitName(unitName);
        unitInfoDao.updateUnitInfo(unitInfo);
        businessResult.setBusinessCode(ResultCode.OK);
        return businessResult;
    }

    @Override
    public PageResult<RoomItem> getRoomList(SearchRoomRequest request) {
        logger.info("search request ===>{}", request.toString());
        List<CommunityInfo> communityInfoList = staffGetCommunity(request.getStaffId(), null);
        Set<Long> communitySet = new LinkedHashSet<>();
        if (request.getCommunityId() != 0) {
            CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(request.getCommunityId());
            communitySet.add(communityInfo.getCommunityId());
        } else {
            for (CommunityInfo communityInfo : communityInfoList) {
                communitySet.add(communityInfo.getCommunityId());
            }
        }
        List<RoomItem> roomItemList = new ArrayList<>();
        if (communitySet.size() != 0) {
            PageHelper.startPage(request.getPage(), request.getCurrentCount(), true, false, null);
            PageHelper.orderBy("estate_id ASC");
            roomItemList = estateInfoDao.selectRoomList(communitySet, request.getFloorNum(), request.getRoomName(), request.getRoomId(), request.getRoomType(), request.getLabelId(), request.getBeginDelivery(), request.getEndDelivery(), request.getStatus());
        }
        PageResult<RoomItem> result = new PageResult<>(roomItemList);
        return result;
    }


    @Override
    public RoomSample getRoomSample(long roomId) {
        RoomInfo roomInfo = roomInfoDao.selectByRoomId(roomId);
        if (roomInfo == null)
            return null;
        RoomSample roomSample = parseRoom(roomInfo);
        return roomSample;
    }

    private RoomSample parseRoom(RoomInfo roomInfo) {
        RoomSample roomSample = new RoomSample(roomInfo);
        if (!StringUtils.isEmpty(roomInfo.getPicture())) {
            List<String> picture = fileService.getPathFile(roomInfo.getPicture(), true);
            roomSample.setPicture(picture);
        }
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(roomInfo.getBuildingId());
        UnitInfo unitInfo = unitInfoDao.selectByUnitId(roomInfo.getUnitId());
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(buildingInfo.getCommunityId());
        roomSample.setDevelopers(communityInfo.getDevelopers());
        roomSample.setBuildingName(buildingInfo.getBuildingName());
        roomSample.setCommunityName(communityInfo.getCommunityName());
        roomSample.setUnitName(unitInfo.getUnitName());
        roomSample.setCommunityId(communityInfo.getCommunityId());
        EstateInfo estateInfo = estateInfoDao.selectByEstateId(roomInfo.getEstateId());
        roomSample.setStatus(estateInfo.getStatus());
        List<LabelResponse> responseList = labelService.getLabel(estateInfo.getLabelId());
        roomSample.setLabelResponseList(responseList);
        return roomSample;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult updateRoom(UpdateRoomRequest request) throws Exception {
        logger.info("updateRequest ===>{}", request.toString());
        logger.info("request ===>{}", request.toString());
        BusinessResult businessResult = new BusinessResult();
        RoomInfo roomInfo = roomInfoDao.selectByRoomId(request.getRoomId());
        if (roomInfo == null) {
            businessResult.setBusinessCode(ResultCode.ROOM_NOT_FOUND);
            return businessResult;
        }
        if (!roomInfo.getRoomName().equals(request.getRoomName())) {
            boolean flag = roomInfoDao.selectExist(request.getRoomName(), roomInfo.getBuildingId(), roomInfo.getUnitId());
            if (flag) {
                businessResult.setBusinessCode(ResultCode.ROOM_EXIST);
                return businessResult;
            }
        }
        StaffInfo staffInfo = staffService.getStaffInfo(request.getStaffId());
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("修改了房屋编号为 -").append(roomInfo.getRoomId()).append("的信息");
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        roomInfo.setRoomName(request.getRoomName());
        roomInfo.setPropertyYear(request.getPropertyYear());
        roomInfo.setRoomType(request.getRoomType());
        roomInfo.setFloorNum(request.getFloorNum());
        roomInfo.setInsideArea(request.getInsideArea());
        roomInfo.setEquallyArea(request.getEquallyArea());
        roomInfo.setRoomArea(request.getRoomArea());
        roomInfo.setHouseType(request.getHouseType());
        roomInfo.setRemark(request.getRemark());
        roomInfo.setDirection(request.getDirection());
        if (!StringUtils.isEmpty(request.getDeliveryTime()))
            roomInfo.setDeliveryTime(TimestampUtils.parse(request.getDeliveryTime(), TimestampUtils.YYYY_MM_DD));
        else
            roomInfo.setDeliveryTime(null);
        if (request.getStatus() != 0)
            estateInfoDao.updateByStatus(roomInfo.getEstateId(), request.getStatus());
        if (request.getPicture() != null) {
            EstateInfo estateInfo = estateInfoDao.selectByEstateId(roomInfo.getEstateId());
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("communityId", estateInfo.getCommunityId());
            variableMap.put("estateId", estateInfo.getEstateId());
            String path = fileService.uploadPictureList(request.getPicture(), PathType.ROOM_PATH, variableMap);
            roomInfo.setPicture(path);
        }
        roomInfoDao.updateRoom(roomInfo);
        return businessResult;
    }

    /**
     * 房屋详情 todo 相关车辆 相关账单
     *
     * @param roomId
     * @return
     */
    @Override
    public RoomResponse getRoomInfo(long roomId) {
        RoomResponse response = new RoomResponse();
        RoomInfo roomInfo = roomInfoDao.selectByRoomId(roomId);
        if (roomInfo == null)
            return null;
        RoomSample roomSample = parseRoom(roomInfo); //房屋基本信息
        response.setRoomSample(roomSample);
        List<EstateChargeItem> chargeItemList = chargeService.getEstateCharge(roomInfo.getEstateId()); //相关收费标准
        response.setEstateChargeItems(chargeItemList);
        List<OwnerEstate> ownerEstates = ownerService.getEstateOwner(roomInfo.getEstateId()); //相关住户
        response.setOwnerEstates(ownerEstates);
        HashSet<CarportItem> carportItems = new HashSet<>();
        HashSet<CarInfoSample> carInfoSamples = new HashSet<>();
        for (OwnerEstate ownerEstate : ownerEstates) {
            List<CarportItem> itemList = ownerService.getOwnerEstateCarport(ownerEstate.getOwnerId(), EstateType.CARPORT);
            carportItems.addAll(itemList);
            List<CarInfoSample> sampleList = ownerService.getOwnerEstateCarInfo(ownerEstate.getOwnerId(), EstateType.CAR);
            carInfoSamples.addAll(sampleList);
        }

        List<PropertyOrderItem> orderItems = chargeService.getRelationOrder(roomInfo.getEstateId(), DataType.ROOM.getDataType());
        response.setOrderItems(orderItems);
        response.setCarInfoSamples(carInfoSamples);
        response.setCarportItems(carportItems);   //相关车位

        return response;
    }

    /**
     * int i 层级关系  0小区 1楼栋 2单元 3房屋   key 为主键
     *
     * @param staffId
     * @param link
     * @return
     */
    @Override
    public List<CommunityLinkage> getLinkage(long staffId, int link) {
        List<CommunityLinkage> linkageList = new ArrayList<>();
        getCommunityLink(staffId, link, 0, linkageList, 0);
        return linkageList;
    }

    @Override
    public EstateInfo getEstateInfo(long estateId) {
        return estateInfoDao.selectByEstateId(estateId);
    }

    @Override
    public RoomInfo getRoomByEstateId(long estateId) {

        return roomInfoDao.selectByEstateId(estateId);
    }

    @Override
    public UnitInfo getUnitInfo(long unitId) {
        return unitInfoDao.selectByUnitId(unitId);
    }

    @Override
    public RoomItem getRoom(long estateId) {
        RoomInfo roomInfo = roomInfoDao.selectByEstateId(estateId);
        if (roomInfo == null)
            return null;
        EstateInfo estateInfo = estateInfoDao.selectByEstateId(estateId);
        List<LabelResponse> labelResponseList = labelService.getLabel(estateInfo.getLabelId());
        RoomItem roomItem = new RoomItem(roomInfo);
        BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingId(roomInfo.getBuildingId());
        UnitInfo unitInfo = unitInfoDao.selectByUnitId(roomInfo.getUnitId());
        roomItem.setBuildingName(buildingInfo.getBuildingName());
        roomItem.setStatus(estateInfo.getStatus());
        roomItem.setUnitName(unitInfo.getUnitName());
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(buildingInfo.getCommunityId());
        roomItem.setCommunityId(communityInfo.getCommunityId());
        roomItem.setCommunityName(communityInfo.getCommunityName());
        roomItem.setLabelResponseList(labelResponseList);
        return roomItem;
    }

    @Override
    public void addEstateLabel(long staffId, List<Long> estateIdList, long labelId) {
        DataLabel dataLabel = labelService.getDataLabel(labelId);
        if (dataLabel == null)
            return;
        List<CommunityInfo> communityInfoList = staffGetCommunity(staffId, null);
        List<EstateInfo> collectionEstate = new ArrayList<>();
        for (CommunityInfo communityInfo : communityInfoList) {
            List<EstateInfo> estateInfoList = new ArrayList<>();
            if (dataLabel.getLabelType() == LabelType.CAR) {
                estateInfoList = estateInfoDao.selectByCommunityId(communityInfo.getCommunityId(), EstateType.CAR);
            } else if (dataLabel.getLabelType() == LabelType.CARPORT) {
                estateInfoList = estateInfoDao.selectByCommunityId(communityInfo.getCommunityId(), EstateType.CARPORT);
            } else if (dataLabel.getLabelType() == LabelType.ROOM) {
                estateInfoList = estateInfoDao.selectByCommunityId(communityInfo.getCommunityId(), EstateType.ROOM);
            }
            collectionEstate.addAll(estateInfoList);
        }
        for (EstateInfo estateInfo : collectionEstate) {
            if (estateIdList.contains(estateInfo.getEstateId())) {
                if (StringUtils.isEmpty(estateInfo.getLabelId())) {
                    estateInfoDao.updateEstateLabel(estateInfo.getEstateId(), String.valueOf(labelId));
                } else {
                    String newLabel = labelService.getNewLabel(estateInfo.getLabelId(), labelId, true);
                    estateInfoDao.updateEstateLabel(estateInfo.getEstateId(), newLabel);
                }
            } else {
                // 取消绑定
                if (!StringUtils.isEmpty(estateInfo.getLabelId())) {
                    String newLabel = labelService.getNewLabel(estateInfo.getLabelId(), labelId, false);
                    estateInfoDao.updateEstateLabel(estateInfo.getEstateId(), newLabel);
                }

            }
        }

    }

    @Override
    public RoomInfo getRoomByRoomId(long roomId) {

        return roomInfoDao.selectByRoomId(roomId);
    }

    @Override
    public List<EstateInfo> getLabelEstate(long labelId, int estateType) {
        return estateInfoDao.selectByLabelId(String.valueOf(labelId), estateType);
    }

    @Override
    public BusinessResult deleteCommunity(long staffId, long communityId) {
        BusinessResult businessResult = new BusinessResult();
        boolean flagBuilder = buildingInfoDao.selectCommunityBuildingExist(communityId);
        if (flagBuilder) {
            businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
            return businessResult;
        }
        boolean flagOwner = ownerService.existOwner(communityId);
        if (flagOwner) {
            businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
            return businessResult;
        }
        boolean estateFlag = estateInfoDao.selectExistCommunityId(communityId);
        if (estateFlag) {
            businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
            return businessResult;
        }
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(communityId);
        if (communityInfo == null) {
            businessResult.setBusinessCode(ResultCode.COMMUNITY_NOT_FOUND);
            return businessResult;
        }
        StaffInfo staffInfo = staffService.getStaffInfo(staffId);
        StringBuilder textBuilder = new StringBuilder("");
        textBuilder.append("删除了小区 -").append(communityInfo.getCommunityName());
        businessResult = systemService.recordHistory(staffInfo, textBuilder.toString(), OperateType.COMMUNITY);
        if (businessResult.getBusinessCode() != ResultCode.OK)
            return businessResult;
        communityInfoDao.delete(communityId);
        return businessResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult deleteBuilding(String buildingIdArr, long staffId) {
        BusinessResult businessResult = new BusinessResult();
        if (!StringUtils.isEmpty(buildingIdArr)) {
            String[] building = buildingIdArr.split("\\|");
            for (String buildingId : building) {
                boolean roomFlag = roomInfoDao.selectBuildingExistRoom(Long.valueOf(buildingId));
                if (roomFlag) {
                    businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
                    return businessResult;
                }
                boolean unitFlag = unitInfoDao.selectExist(Long.valueOf(buildingId), null);
                if (unitFlag) {
                    businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
                    return businessResult;
                }
                buildingInfoDao.delete(Long.valueOf(buildingId));
                unitInfoDao.deleteByBuildingId(Long.valueOf(buildingId));
            }
        }

        return businessResult;
    }

    @Override
    public BusinessResult deleteUnit(String unitIdArr, long staffId) {
        BusinessResult businessResult = new BusinessResult();
        if (!StringUtils.isEmpty(unitIdArr)) {
            String[] unit = unitIdArr.split("\\|");
            boolean roomFlag = roomInfoDao.selectUnitRoomExist(unit);
            if (roomFlag) {
                businessResult.setBusinessCode(ResultCode.CAN_NOT_DELETE);
                return businessResult;
            }
            unitInfoDao.delete(unit);
        /*    for (String unitId : unit) {
                boolean roomFlag = roomInfoDao.selectUnitRoomExist(Long.valueOf(unitId));


            }*/
        }

        return businessResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessResult deleteRoom(String roomIdArr, long staffId) {
        BusinessResult businessResult = new BusinessResult();
        if (!StringUtils.isEmpty(roomIdArr)) {
            String[] room = roomIdArr.split("\\|");
            for (String roomId : room) {
                RoomInfo roomInfo = roomInfoDao.selectByRoomId(Long.valueOf(roomId));
                if (roomInfo == null)
                    continue;
                estateInfoDao.delete(roomInfo.getEstateId());
                ownerService.deleteEstateRelation(roomInfo.getEstateId());
                chargeService.deleteEstateNormal(roomInfo.getEstateId());
                roomInfoDao.delete(roomInfo.getRoomId());
            }
        }

        return businessResult;
    }

    @Override
    public void deleteEstate(long estateId) {
        estateInfoDao.delete(estateId);
    }

    @Override
    public List<CommunityInfo> companyGetCommunity(long companyId, String communityName) {
        return communityInfoDao.selectByCompanyId(communityName, companyId);
    }

    @Override
    public List<BuildingInfo> getCommunityBuilding(long communityId) {
        return buildingInfoDao.selectByCommunityId(communityId, null, null);
    }

    @Override
    public List<UnitInfo> getBuildingUnit(long buildingId) {
        return unitInfoDao.selectByBuildingId(buildingId);
    }

    @Override
    public List<RoomInfo> getUnitRoom(long unitId) {
        return roomInfoDao.selectByUnitId(unitId);
    }

    @Override
    public BuildingInfo getBuilding(long buildingId) {
        return buildingInfoDao.selectByBuildingId(buildingId);
    }

    @Override
    public List<CashParamResponse> getCashParam(SearchCashParamRequest request) {
        List<CashParamResponse> responseList = new ArrayList<>();
        List<CommunityInfo> communityInfos = staffGetCommunity(request.getStaffId(), null);
        Map<Long, String> map = new HashMap<>();
        for (CommunityInfo communityInfo : communityInfos) {
            map.put(communityInfo.getCommunityId(), communityInfo.getCommunityName());
        }
        if (request.getDataType() == DataType.OWNER.getDataType()) {
            List<OwnerInfo> ownerInfoList = ownerService.getOwnerParam(map.keySet(), request.getDataName());
            for (OwnerInfo ownerInfo : ownerInfoList) {
                CashParamResponse response = new CashParamResponse();
                response.setDataType(DataType.OWNER.getDataType());
                response.setCommunityName(map.get(ownerInfo.getCommunityId()));
                response.setDataId(ownerInfo.getOwnerId());
                if (StringUtils.isEmpty(ownerInfo.getOwnerName()))
                    response.setDataName("-- --" + ownerInfo.getOwnerPhone());
                else
                    response.setDataName(ownerInfo.getOwnerName() + "--" + ownerInfo.getOwnerPhone());
                responseList.add(response);
            }
        } else {
            List<EstateInfo> estateInfoList = estateInfoDao.selectCommunityParam(request.getDataType(), request.getDataName(), map.keySet());
            for (EstateInfo estateInfo : estateInfoList) {
                CashParamResponse response = new CashParamResponse();
                response.setCommunityName(map.get(estateInfo.getCommunityId()));
                response.setDataId(estateInfo.getEstateId());
                if (estateInfo.getEstateTypeId() == EstateType.ROOM) {
                    response.setDataType(DataType.ROOM.getDataType());
                    RoomInfo roomInfo = roomInfoDao.selectByEstateId(estateInfo.getEstateId());
                    response.setDataName(roomInfo.getRoomName());
                } else if (estateInfo.getEstateTypeId() == EstateType.CARPORT) {
                    Carport carport = carportService.getCarport(estateInfo.getEstateId());
                    response.setDataName(carport.getCarportNum());
                    response.setDataType(DataType.CARPORT.getDataType());
                } else {
                    CarInfo carInfo = carService.getCarInfo(estateInfo.getEstateId());
                    response.setDataType(DataType.CAR.getDataType());
                    response.setDataName(carInfo.getCarNum());
                }
                responseList.add(response);
            }
        }
        return responseList;
    }

    @Override
    public Object cashSample(long dataId, int dataType) {
        Map<Object, Object> map = new HashMap<>();
        if (dataType == DataType.OWNER.getDataType()) {
            OwnerInfoSample ownerInfoSample = ownerService.getOwnerSample(dataId);
            return ownerInfoSample;
        } else if (dataType == DataType.ROOM.getDataType()) {
            RoomInfo roomInfo = roomInfoDao.selectByEstateId(dataId);
            RoomSample roomSample = parseRoom(roomInfo);
            List<OwnerEstate> infoItems = ownerService.getEstateOwner(dataId);
            map.put("data", roomSample);
            map.put("owner", infoItems);
            return map;
        } else if (dataType == DataType.CARPORT.getDataType()) {
            Carport carport = carportService.getCarport(dataId);
            CarportItem carportItem = carportService.getCarportItem(carport.getCarportId());
            List<OwnerEstate> infoItems = ownerService.getEstateOwner(dataId);
            map.put("data", carportItem);
            map.put("owner", infoItems);
            return map;
        } else {
            CarInfo carInfo = carService.getCarInfo(dataId);
            CarInfoSample carInfoSample = carService.getCarSample(carInfo.getCarId());
            List<OwnerEstate> infoItems = ownerService.getEstateOwner(dataId);
            map.put("data", carInfoSample);
            map.put("owner", infoItems);
            return map;
        }
    }

    @Override
    public List<CommunityLinkage> chooseEstate(long communityId, int estateType, int link) {
        if (estateType == EstateType.ROOM) {
            List<CommunityLinkage> linkageList = new ArrayList<>();
            getCommunity(communityId, link, 0, linkageList, 0);
            return linkageList;
        } else if (estateType == EstateType.CAR) {
            List<CommunityLinkage> linkageList = new ArrayList<>();
            getCar(communityId, 0, linkageList, 0);
            return linkageList;
        } else {
            List<CommunityLinkage> linkageList = new ArrayList<>();
            getCarPort(communityId, 0, linkageList, 0);
            return linkageList;
        }
    }

    @Override  //todo 房屋导出
    public ExportFileResponse exportRoom(long communityId, long staffId) throws Exception {
        ExportFileResponse response = new ExportFileResponse();
        CommunityInfo communityInfo = communityInfoDao.selectByCommunityId(communityId);
        List<RoomInfoFile> roomInfoFileList = new ArrayList<>();
        roomInfoFileList.add(RoomInfoFile.getTitle());
        List<RoomSample> roomSampleList = estateInfoDao.selectCommunityRoom(communityId);
        for (RoomSample roomSample : roomSampleList) {
            RoomInfoFile roomInfoFile = new RoomInfoFile(roomSample);
            List<OwnerInfo> ownerInfoList = ownerService.getEstateOwnerType(roomSample.getEstateId(), 1);
            if (!ownerInfoList.isEmpty()){
                StringBuilder reservedPhone = new StringBuilder("");
                StringBuilder reservedName = new StringBuilder("");
                for (OwnerInfo ownerInfo : ownerInfoList) {
                    reservedName.append(ownerInfo.getOwnerName()).append(",");
                    reservedPhone.append(ownerInfo.getOwnerPhone()).append(",");
                }
                roomInfoFile.setReservedName(reservedName.toString().substring(0, reservedName.toString().length() - 1));
                roomInfoFile.setReservedPhone(reservedPhone.toString().substring(0, reservedPhone.toString().length() - 1));
            }
            roomInfoFileList.add(roomInfoFile);
        }
        Map<String, List<RoomInfoFile>> roomInfoMap = new LinkedHashMap<>();
        roomInfoMap.put("房屋列表", roomInfoFileList);
        String fileName = communityInfo.getCommunityName() + "导出房屋信息.xlsx";
        XSSFWorkbook workbook = ExcelUtil.exportFile(roomInfoMap, RoomInfoFile.class);
        response.setFileName(fileName);
        response.setWorkbook(workbook);
        return response;
    }

    private void getCarPort(long communityId, int level, List<CommunityLinkage> linkageList, int key) {
        if (level == 0) {
            List<Carport> carportList = carportService.getCommunity(communityId);
            for (Carport carport : carportList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(carport.getCarportId());
                linkage.setValue(carport.getCarportNum());
                linkage.setLevel(level);
                linkage.setEstateId(carport.getEstateId());
                List<CommunityLinkage> communityLinkageList = new ArrayList<>();
                linkage.setChildren(communityLinkageList);
                linkageList.add(linkage);
                getCar(communityId, 1, linkage.getChildren(), carport.getEstateId());
            }
        }
        if (level == 1) {
            List<OwnerInfo> infoList = ownerService.getEstateOwnerType(key, 1);
            for (OwnerInfo ownerInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(ownerInfo.getOwnerId());
                Map<String, Object> map = new HashMap<>();
                map.put("ownerPhone", ownerInfo.getOwnerPhone());
                map.put("ownerName", ownerInfo.getOwnerName());
                linkage.setValue(map);
                linkage.setLevel(level);
                List<CommunityLinkage> ownerLinkageList = new ArrayList<>();
                linkage.setChildren(ownerLinkageList);
                linkageList.add(linkage);
            }
        }
    }

    private void getCar(long communityId, int level, List<CommunityLinkage> linkageList, long key) {
        if (level == 0) {
            List<CarInfo> carInfoList = carService.getCommunity(communityId);
            for (CarInfo carInfo : carInfoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(carInfo.getCarId());
                linkage.setValue(carInfo.getCarNum());
                linkage.setLevel(level);
                linkage.setEstateId(carInfo.getEstateId());
                List<CommunityLinkage> communityLinkageList = new ArrayList<>();
                linkage.setChildren(communityLinkageList);
                linkageList.add(linkage);
                getCar(communityId, 1, linkage.getChildren(), carInfo.getEstateId());
            }
        }
        if (level == 1) {
            List<OwnerInfo> infoList = ownerService.getEstateOwnerType(key, 1);
            for (OwnerInfo ownerInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(ownerInfo.getOwnerId());
                Map<String, Object> map = new HashMap<>();
                map.put("ownerPhone", ownerInfo.getOwnerPhone());
                map.put("ownerName", ownerInfo.getOwnerName());
                linkage.setValue(map);
                linkage.setLevel(level);
                List<CommunityLinkage> ownerLinkageList = new ArrayList<>();
                linkage.setChildren(ownerLinkageList);
                linkageList.add(linkage);
            }
        }
    }

    private void getCommunity(long communityId, int link, int level, List<CommunityLinkage> linkageList, long key) {
        if (level == 0) {
            List<BuildingInfo> buildingInfos = buildingInfoDao.selectByCommunityId(communityId, null, null);
            for (BuildingInfo buildingInfo : buildingInfos) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(buildingInfo.getBuildingId());
                linkage.setValue(buildingInfo.getBuildingName());
                List<CommunityLinkage> buildingLinkageList = new ArrayList<>();
                linkage.setChildren(buildingLinkageList);
                linkage.setLevel(level);
                linkageList.add(linkage);
                if (link == 0)
                    continue;
                getCommunity(communityId, link, 1, linkage.getChildren(), buildingInfo.getBuildingId());
            }
        }
        if (level == 1) {
            List<UnitInfo> infoList = unitInfoDao.selectByBuildingId(key);
            for (UnitInfo unitInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(unitInfo.getUnitId());
                linkage.setValue(unitInfo.getUnitName());
                linkage.setLevel(level);
                List<CommunityLinkage> unitLinkageList = new ArrayList<>();
                linkage.setChildren(unitLinkageList);
                linkageList.add(linkage);
                if (link == 1)
                    continue;
                getCommunity(communityId, link, 2, linkage.getChildren(), unitInfo.getUnitId());
            }
        }
        if (level == 2) {
            List<RoomInfo> infoList = roomInfoDao.selectByUnitId(key);
            for (RoomInfo roomInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(roomInfo.getRoomId());
                linkage.setValue(roomInfo.getRoomName());
                linkage.setLevel(level);
                linkage.setEstateId(roomInfo.getEstateId());
                List<CommunityLinkage> roomLinkageList = new ArrayList<>();
                linkage.setChildren(roomLinkageList);
                linkageList.add(linkage);
                if (link == 2)
                    continue;
                getCommunity(communityId, link, 3, linkage.getChildren(), roomInfo.getEstateId());
            }
        }
        if (level == 3) {
            List<OwnerInfo> infoList = ownerService.getEstateOwnerType(key, 1);
            for (OwnerInfo ownerInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(ownerInfo.getOwnerId());
                Map<String, Object> map = new HashMap<>();
                map.put("ownerPhone", ownerInfo.getOwnerPhone());
                map.put("ownerName", ownerInfo.getOwnerName());
                linkage.setValue(map);
                linkage.setLevel(level);
                List<CommunityLinkage> ownerLinkageList = new ArrayList<>();
                linkage.setChildren(ownerLinkageList);
                linkageList.add(linkage);
            }
        }
    }

    private void getCommunityLink(long staffId, int link, int level, List<CommunityLinkage> linkageList, long key) {
        if (level == 0) {
            List<CommunityInfo> infoList;
            if (staffId != 0)
                infoList = staffGetCommunity(staffId, "");
            else
                infoList = communityInfoDao.selectAll();
            for (CommunityInfo communityInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(communityInfo.getCommunityId());
                linkage.setValue(communityInfo.getCommunityName());
                linkage.setLevel(level);
                List<CommunityLinkage> communityLinkageList = new ArrayList<>();
                linkage.setChildren(communityLinkageList);
                linkageList.add(linkage);
                if (link == 0)
                    continue;
                getCommunityLink(staffId, link, 1, linkage.getChildren(), communityInfo.getCommunityId());
            }
        }
        if (level == 1) {
            List<BuildingInfo> infoList = buildingInfoDao.selectByCommunityId(key, null, null);
            for (BuildingInfo buildingInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(buildingInfo.getBuildingId());
                linkage.setValue(buildingInfo.getBuildingName());
                List<CommunityLinkage> buildingLinkageList = new ArrayList<>();
                linkage.setChildren(buildingLinkageList);
                linkage.setLevel(level);
                linkageList.add(linkage);
                if (link == 1)
                    continue;
                getCommunityLink(staffId, link, 2, linkage.getChildren(), buildingInfo.getBuildingId());
            }
        }
        if (level == 2) {
            List<UnitInfo> infoList = unitInfoDao.selectByBuildingId(key);
            for (UnitInfo unitInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(unitInfo.getUnitId());
                linkage.setValue(unitInfo.getUnitName());
                linkage.setLevel(level);
                List<CommunityLinkage> unitLinkageList = new ArrayList<>();
                linkage.setChildren(unitLinkageList);
                linkageList.add(linkage);
                if (link == 2)
                    continue;
                getCommunityLink(staffId, link, 3, linkage.getChildren(), unitInfo.getUnitId());
            }
        }
        if (level == 3) {
            List<RoomInfo> infoList = roomInfoDao.selectByUnitId(key);
            for (RoomInfo roomInfo : infoList) {
                CommunityLinkage linkage = new CommunityLinkage();
                linkage.setKey(roomInfo.getRoomId());
                linkage.setValue(roomInfo.getRoomName());
                linkage.setLevel(level);
                linkage.setEstateId(roomInfo.getEstateId());
                List<CommunityLinkage> roomLinkageList = new ArrayList<>();
                linkage.setChildren(roomLinkageList);
                linkageList.add(linkage);
                //getCommunityLink(staffId, force, 3, linkageList, roomInfo.getUnitId());
            }
        }
    }

    private RoomInfo getRoomInfo(Row rows, HashSet<BuildingUnit> buildingUnitHashSet) throws Exception {
        RoomInfo roomInfo = new RoomInfo();
        String buildingName = rows.getCell(0).getStringCellValue(); //楼宇名称
        String unitName = rows.getCell(1).getStringCellValue(); //单元名称
        for (BuildingUnit buildingUnit : buildingUnitHashSet) {
            if (buildingUnit.getUnitName().equals(unitName) && buildingUnit.getBuildingName().equals(buildingName)) {
                roomInfo.setBuildingId(buildingUnit.getBuildingId());
                roomInfo.setUnitId(buildingUnit.getUnitId());
            }
        }
        roomInfo.setRoomName(rows.getCell(2).getStringCellValue());//房间名称
        double floorNum = rows.getCell(3).getNumericCellValue();
        roomInfo.setFloorNum(new Double(floorNum).intValue());
        double roomArea = rows.getCell(4).getNumericCellValue();
        roomInfo.setRoomArea(new Double(roomArea * 100).intValue()); //建筑面积
        double insideArea = rows.getCell(5).getNumericCellValue();
        roomInfo.setInsideArea(new Double(Math.round(insideArea * 100)).intValue()); //套内面积
        double equallyArea = rows.getCell(6).getNumericCellValue();
        roomInfo.setEquallyArea(new Double(Math.round(equallyArea * 100)).intValue()); //公摊面积
        String roomType = rows.getCell(7).getStringCellValue();  //房屋类型
        if (StringUtils.isEmpty(roomType))
            roomInfo.setRoomType(RoomType.OTHER.getRoomType());
        if (roomType.equals(RoomType.APARTMENT.getTypeName()))
            roomInfo.setRoomType(RoomType.APARTMENT.getRoomType());
        if (roomType.equals(RoomType.OFFICE.getTypeName()))
            roomInfo.setRoomType(RoomType.OFFICE.getRoomType());
        if (roomType.equals(RoomType.PLANT.getTypeName()))
            roomInfo.setRoomType(RoomType.PLANT.getRoomType());
        if (roomType.equals(RoomType.WAREHOUSE.getTypeName()))
            roomInfo.setRoomType(RoomType.WAREHOUSE.getRoomType());
        if (roomType.equals(RoomType.STORE.getTypeName()))
            roomInfo.setRoomType(RoomType.STORE.getRoomType());
        if (roomType.equals(RoomType.HOTEL.getTypeName()))
            roomInfo.setRoomType(RoomType.HOTEL.getRoomType());
        roomInfo.setDirection(rows.getCell(8).getStringCellValue()); //房屋朝向
        double propertyYear = rows.getCell(10).getNumericCellValue(); //产权年限
        roomInfo.setPropertyYear(new Double(propertyYear).intValue());
        String houseType = rows.getCell(11).getStringCellValue();
        roomInfo.setHouseType(houseType); //户型
        roomInfo.setRemark(rows.getCell(14).getStringCellValue());//备注
        logger.info("roomInfo ===>{}", roomInfo.toString());
        return roomInfo;
    }

    /**
     * 楼栋单元
     *
     * @param sheet
     * @param communityId
     * @return
     */
    private HashSet<BuildingUnit> getBuildingUnit(XSSFSheet sheet, long communityId) {
        HashSet<BuildingUnit> buildingUnitHashSet = new HashSet<>();
        for (Row rows : sheet) {
            if (rows.getRowNum() != 0) { //略过title
                if (rows != null) {
                    String buildingName = rows.getCell(0).getStringCellValue(); //楼宇名称
                    String unitName = rows.getCell(1).getStringCellValue(); //单元名称
                    buildingUnitHashSet.add(new BuildingUnit(buildingName, unitName));
                }
            }
        }
        for (BuildingUnit buildingUnit : buildingUnitHashSet) {
            BuildingInfo buildingInfo = buildingInfoDao.selectByBuildingName(communityId, buildingUnit.getBuildingName());
            if (buildingInfo == null) {
                buildingInfo = new BuildingInfo();
                buildingInfo.setBuildingName(buildingUnit.getBuildingName());
                buildingInfo.setCommunityId(communityId);
                buildingInfoDao.insert(buildingInfo);
                buildingUnit.setBuildingId(buildingInfo.getBuildingId()); //楼栋Id
            } else {
                buildingUnit.setBuildingId(buildingInfo.getBuildingId()); //楼栋Id
            }
            UnitInfo unitInfo = unitInfoDao.selectByUnitName(buildingInfo.getBuildingId(), buildingUnit.getUnitName());
            if (unitInfo == null) {
                unitInfo = new UnitInfo();
                unitInfo.setUnitName(buildingUnit.getUnitName());
                unitInfo.setBuildingId(buildingUnit.getBuildingId());
                unitInfoDao.insert(unitInfo);
                buildingUnit.setUnitId(unitInfo.getUnitId()); //单元Id
            } else {
                buildingUnit.setUnitId(unitInfo.getUnitId()); //单元Id
            }
        }
        return buildingUnitHashSet;
    }


}
