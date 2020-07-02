package com.xinge.yijia.server.web.controller;

import com.xinge.yijia.server.common.business.BusinessResult;
import com.xinge.yijia.server.common.business.CommonResult;
import com.xinge.yijia.server.common.business.ResultCode;
import com.xinge.yijia.server.common.message.*;
import com.xinge.yijia.server.common.message.request.*;
import com.xinge.yijia.server.common.message.response.*;
import com.xinge.yijia.server.web.service.ICommunityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;

/**
 * @Auther: lsj
 * @Date: Created in 9:11 2019/3/19
 * @Description: 小区  楼宇 房屋 单元
 */
@RequestMapping(value = "/community")
@RestController
public class CommunityControl {
    private static final Logger logger = LoggerFactory.getLogger(CommunityControl.class);

    @Autowired
    private ICommunityService communityService;

    /**
     * 创建小区
     *
     * @param staffId 操作人员Id
     * @param request
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.POST)
    public CommonResult createCommunity(long staffId, CommunityRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.createCommunity(staffId, request);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(businessResult);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 导入房屋
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/room/import", method = RequestMethod.POST)
    public CommonResult importRoom(ImportFileRequest request) {
        CommonResult commonResult = new CommonResult();
        BusinessResult businessResult = new BusinessResult();
        try {
            businessResult = communityService.importRoom(request);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            businessResult.setBusinessCode(ResultCode.EXCEL_MESSAGE_ERROR);
            commonResult.setResultCode(ResultCode.OK);
            logger.error("###Exception ===>{}", e);
        }
        commonResult.setObject(businessResult);
        return commonResult;
    }

    /**
     * 获取小区列表
     *
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public CommonResult staffGetCommunity(SearchCommunityRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            PageResult<CommunityInfoItem> response = communityService.staffGetCommunity(request);
            commonResult.setObject(response);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 小区信息修改
     *
     * @param staffId          操作员工Id
     * @param communityId      小区Id
     * @param communityRequest
     * @return
     */
    @RequestMapping(value = "/revamp", method = RequestMethod.POST)
    public CommonResult RedactCommunity(long staffId, long communityId, CommunityRequest communityRequest) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.staffSetCommunity(staffId, communityId, communityRequest);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 获取单个小区
     *
     * @param communityId 小区Id
     * @return
     */
    @RequestMapping(value = "/id", method = RequestMethod.GET)
    public CommonResult getCommunityInfo(long communityId) {
        CommonResult commonResult = new CommonResult();
        try {
            CommunityInfoItem infoItem = communityService.getCommunityInfo(communityId);
            commonResult.setObject(infoItem);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 楼宇创建
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/building", method = RequestMethod.POST)
    public CommonResult createBuilding(CreateBuildingRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.createBuilding(request);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(businessResult);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 创建楼宇单元
     *
     * @param unitName   单元名称
     * @param buildingId 楼宇Id
     * @param staffId    员工Id
     * @return
     */
    @RequestMapping(value = "/unit", method = RequestMethod.POST)
    public CommonResult createUnit(String unitName, long buildingId, long staffId) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.createUnit(unitName, buildingId, staffId);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 楼宇获取
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/building/list", method = RequestMethod.GET)
    public CommonResult buildingList(SearchBuildingRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            PageResult<BuildingInfoItem> response = communityService.getBuildingList(request);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(response);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 单个楼栋信息获取
     *
     * @param buildingId 楼宇Id
     * @return
     */
    @RequestMapping(value = "/building/id", method = RequestMethod.GET)
    public CommonResult buildingInfo(long buildingId) {
        CommonResult commonResult = new CommonResult();
        try {
            BuildingInfoItem infoItem = communityService.getBuildingInfo(buildingId);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(infoItem);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 单元列表
     *
     * @param buildingId   楼宇Id
     * @param page         当前页数
     * @param currentCount 每页条数
     * @return
     */
    @RequestMapping(value = "/unit/list", method = RequestMethod.GET)
    public CommonResult getUnitList(long buildingId, int page, int currentCount) {
        CommonResult commonResult = new CommonResult();
        try {
            PageResult<UnitInfoItem> response = communityService.getUnitList(buildingId, page, currentCount);
            commonResult.setObject(response);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 房屋创建  todo 目前仿照微小区
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/room", method = RequestMethod.POST)
    public CommonResult createRoom(CreateRoomRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.createRoom(request);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 楼栋编辑
     *
     * @param: request
     * @auther: lucy
     * @return:
     */
    @RequestMapping(value = "/building/revamp", method = RequestMethod.PUT)
    public CommonResult buildingRedact(UpdateBuildingRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.setBuildingRedact(request);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 房屋列表
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/room/list", method = RequestMethod.GET)
    public CommonResult roomList(SearchRoomRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            PageResult<RoomItem> response = communityService.getRoomList(request);
            commonResult.setObject(response);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 房屋信息
     *
     * @param roomId 房屋Id
     * @return
     */
    @RequestMapping(value = "/room/sample", method = RequestMethod.GET)
    public CommonResult roomSample(long roomId) {
        CommonResult commonResult = new CommonResult();
        try {
            RoomSample roomSample = communityService.getRoomSample(roomId);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(roomSample);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * @param staffId  员工Id
     * @param unitId   单元Id
     * @param unitName 单元名称
     * @return
     * @auther: lucy
     * @date: 2019-3-26 10:23:08
     */
    @RequestMapping(value = "/unit/revamp", method = RequestMethod.PUT)
    public CommonResult redactUnit(long staffId, long unitId, String unitName) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.setUnitInfo(staffId, unitId, unitName);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 房屋信息修改
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/room/revamp", method = RequestMethod.POST)
    public CommonResult updateRoom(UpdateRoomRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.updateRoom(request);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * todo 房屋详细信息
     *
     * @param roomId
     * @return
     */
    @RequestMapping(value = "/room/info", method = RequestMethod.GET)
    public CommonResult getRoomInfo(long roomId) {
        CommonResult commonResult = new CommonResult();
        try {
            RoomResponse roomResponse = communityService.getRoomInfo(roomId);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(roomResponse);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 房屋导出
     *
     * @param request
     * @param response
     * @param communityId
     * @return
     */
    @RequestMapping(value = "/room/export", method = RequestMethod.GET)
    public CommonResult exportRoom(HttpServletRequest request, HttpServletResponse response, long communityId, long staffId) {
        CommonResult commonResult = new CommonResult();
        try {
            ExportFileResponse exportResponse = communityService.exportRoom(communityId, staffId);
            String fileName;
            if ((request.getHeader("User-Agent").toUpperCase().indexOf("MSIE") > 0) || (request.getHeader("User-Agent").contains("Trident"))) {
                fileName = URLEncoder.encode(exportResponse.getFileName(), "UTF-8");
            } else {
                fileName = new String(exportResponse.getFileName().getBytes("UTF-8"), "ISO8859-1");
            }
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);//默认Excel名称
            response.flushBuffer();
            exportResponse.getWorkbook().write(response.getOutputStream());
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 小区楼宇单元房屋选择 关联
     *
     * @param staffId
     * @param link
     * @return
     */
    @RequestMapping(value = "/linkage", method = RequestMethod.GET)
    public CommonResult communityLinkage(long staffId, int link) {
        CommonResult commonResult = new CommonResult();
        try {
            List<CommunityLinkage> linkageList = communityService.getLinkage(staffId, link);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(linkageList);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 删除小区
     *
     * @param staffId     操作人员Id
     * @param communityId 小区Id
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public CommonResult deleteCommunity(long staffId, long communityId) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.deleteCommunity(staffId, communityId);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 楼宇删除
     *
     * @param buildingIdArr 楼宇Id多个|隔开
     * @param staffId       操作人员Id
     * @return
     */
    @RequestMapping(value = "/building", method = RequestMethod.DELETE)
    public CommonResult deleteBuilding(String buildingIdArr, long staffId) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.deleteBuilding(buildingIdArr, staffId);
            commonResult.setResultCode(ResultCode.OK);
            commonResult.setObject(businessResult);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 删除单元
     *
     * @param unitIdArr 单元Id多个|隔开
     * @param staffId   操作人员Id
     * @return
     */
    @RequestMapping(value = "/unit", method = RequestMethod.DELETE)
    public CommonResult deleteUnit(String unitIdArr, long staffId) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.deleteUnit(unitIdArr, staffId);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 删除房屋
     *
     * @param roomIdArr 房屋Id多个|隔开
     * @param staffId   操作人员Id
     * @return
     */
    @RequestMapping(value = "/room", method = RequestMethod.DELETE)
    public CommonResult deleteRoom(String roomIdArr, long staffId) {
        CommonResult commonResult = new CommonResult();
        try {
            BusinessResult businessResult = communityService.deleteRoom(roomIdArr, staffId);
            commonResult.setObject(businessResult);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            commonResult.setResultCode(ResultCode.FAIL);
            logger.error("###Exception ===>{}", e);
        }
        return commonResult;
    }

    /**
     * 收银台 选择搜索
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/cashParam", method = RequestMethod.GET)
    public CommonResult cashParamList(SearchCashParamRequest request) {
        CommonResult commonResult = new CommonResult();
        try {
            List<CashParamResponse> responses = communityService.getCashParam(request);
            commonResult.setObject(responses);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("###Exception ===>{}", e);
            commonResult.setResultCode(ResultCode.FAIL);
        }
        return commonResult;
    }

    /**
     * 收银台调用接口
     *
     * @param dataId  数据Id
     * @param dataType 数据类型
     * @return
     */
    @RequestMapping(value = "/cash/sample", method = RequestMethod.GET)
    public CommonResult cashSample(long dataId, int dataType) {
        CommonResult commonResult = new CommonResult();
        try {
            Object response = communityService.cashSample(dataId, dataType);
            commonResult.setObject(response);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("###Exception ===>{}", e);
            commonResult.setResultCode(ResultCode.FAIL);
        }
        return commonResult;
    }

    /**
     * 认证选择资产 获取资产列表
     *
     * @param communityId 小区Id
     * @param estateType  资产类型
     * @return
     */
    @RequestMapping(value = "/choose", method = RequestMethod.GET)
    public CommonResult chooseEstate(long communityId, int estateType, int link) {
        CommonResult commonResult = new CommonResult();
        try {
            List<CommunityLinkage> chooseEstate = communityService.chooseEstate(communityId, estateType, link);
            commonResult.setObject(chooseEstate);
            commonResult.setResultCode(ResultCode.OK);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("###Exception ===>{}", e);
            commonResult.setResultCode(ResultCode.FAIL);
        }
        return commonResult;
    }
}
