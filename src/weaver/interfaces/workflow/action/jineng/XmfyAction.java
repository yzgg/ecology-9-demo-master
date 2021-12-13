package weaver.interfaces.workflow.action.jineng;

import weaver.conn.RecordSet;
import weaver.conn.RecordSetTrans;
import weaver.general.Util;
import weaver.interfaces.workflow.action.Action;
import weaver.soa.workflow.request.RequestInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class XmfyAction implements Action {
    @Override
    public String execute(RequestInfo requestInfo) {
        //提交节点
        RecordSetTrans recordSetTrans = new RecordSetTrans();
        recordSetTrans.setAutoCommit(false);
        RequestTableInfoToMap requestTableInfoToMap = new RequestTableInfoToMap(requestInfo);
        Map<String, String> mainTable = requestTableInfoToMap.getMainTableMap();
        String xmbhwb = Util.null2String(mainTable.get("xmbhwb"));
        String[] xkzclkm = {"1","2","4"};////需要控制的科目 1材料费、2人工费、4业务费
        ArrayList<HashMap<String, String>> detailTables = requestTableInfoToMap.getDetailTableMaps(0);
        for (HashMap<String, String> detailTable : detailTables) {
            String mxid = Util.null2String(detailTable.get("mxid"));
            String mx1bxje = Util.null2String(detailTable.get("mx1bxje"));
            Double mx1bxje0 = Double.parseDouble(mx1bxje);
            String sykfkje0 = Util.null2String(detailTable.get("sykfkje"));
            String clkm = Util.null2String(detailTable.get("bxkm"));//需要控制的科目 材料费、人工费、业务费
            if (sykfkje0.isEmpty()) {
                requestInfo.getRequestManager().setMessagecontent("剩余可付款金额不能为空，流程无法提交");
                return FAILURE_AND_CONTINUE;
            } else {
                Double sykfkje = Double.parseDouble(sykfkje0);
                if (Arrays.asList(xkzclkm).contains(clkm) && mx1bxje0 > sykfkje) {
                    requestInfo.getRequestManager().setMessagecontent("报销金额不能大于剩余可付款金额");
                    return FAILURE_AND_CONTINUE;
                } else {
                    if (mxid.isEmpty()) {
                        requestInfo.getRequestManager().setMessagecontent("明细id不能为空，流程无法提交");
                        return FAILURE_AND_CONTINUE;
                    }
                    //获取项目台账明细表数据
                    RecordSet selectXmbRS = new RecordSet();
                    String selectXmbSQL = "select * from uf_xmb_dt1 a inner join uf_xmb b on a.mainid=b.id where b.xmbh='" + xmbhwb + "'";
                    String mainid = null;
                    if (selectXmbRS.execute(selectXmbSQL) && selectXmbRS.next()) {
                        mainid = Util.null2String(selectXmbRS.getString("mainid"));
                    } else {
                        requestInfo.getRequestManager().setMessagecontent("未查找到项目台账数据");

                    }

//回写数据
                    String updateXmbSQL = "update uf_xmb_dt1 set djje=isnull(djje,0)+?,sykfkje=isnull(sykfkje,0)-? where mainid=? and mxid=?";
                    try {
                        recordSetTrans.executeUpdate(updateXmbSQL, mx1bxje, mx1bxje, mainid, mxid);
                    } catch (Exception e) {
                        recordSetTrans.rollback();
                        recordSetTrans.setAutoCommit(true);
                        requestInfo.getRequestManager().setMessagecontent("没有更新数据");
                        return FAILURE_AND_CONTINUE;
                    }

                }

            }
        }
        try {
            recordSetTrans.commit();
            recordSetTrans.setAutoCommit(true);
            return SUCCESS;
        } catch (Exception e) {
            recordSetTrans.rollback();
            recordSetTrans.setAutoCommit(true);
            requestInfo.getRequestManager().setMessagecontent("回写数据至项目台账失败");
            return FAILURE_AND_CONTINUE;
        }
    }
}