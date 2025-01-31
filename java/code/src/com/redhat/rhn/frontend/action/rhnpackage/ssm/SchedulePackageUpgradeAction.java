/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.frontend.action.rhnpackage.ssm;

import static com.redhat.rhn.common.util.DatePicker.YEAR_RANGE_POSITIVE;

import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.messaging.MessageQueue;
import com.redhat.rhn.common.util.DatePicker;
import com.redhat.rhn.domain.action.ActionChain;
import com.redhat.rhn.domain.action.ActionFactory;
import com.redhat.rhn.domain.rhnset.RhnSet;
import com.redhat.rhn.domain.rhnset.SetCleanup;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.action.MaintenanceWindowsAware;
import com.redhat.rhn.frontend.dto.PackageListItem;
import com.redhat.rhn.frontend.events.SsmUpgradePackagesEvent;
import com.redhat.rhn.frontend.struts.ActionChainHelper;
import com.redhat.rhn.frontend.struts.MaintenanceWindowHelper;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.RhnHelper;
import com.redhat.rhn.frontend.struts.SessionSetHelper;
import com.redhat.rhn.frontend.struts.StrutsDelegate;
import com.redhat.rhn.frontend.taglibs.list.TagHelper;
import com.redhat.rhn.frontend.taglibs.list.helper.ListHelper;
import com.redhat.rhn.frontend.taglibs.list.helper.Listable;
import com.redhat.rhn.manager.rhnset.RhnSetDecl;
import com.redhat.rhn.manager.rhnset.RhnSetManager;
import com.redhat.rhn.manager.ssm.SsmManager;
import com.redhat.rhn.manager.system.SystemManager;
import com.redhat.rhn.taskomatic.TaskomaticApi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.action.DynaActionForm;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 */
public class SchedulePackageUpgradeAction extends RhnAction implements Listable, MaintenanceWindowsAware {

    private static Logger log = LogManager.getLogger(SchedulePackageUpgradeAction.class);

    /** Taskomatic API instance */
    private static final TaskomaticApi TASKOMATIC_API = new TaskomaticApi();

    /** {@inheritDoc} */
    @Override
    public ActionForward execute(ActionMapping actionMapping,
                                 ActionForm actionForm,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {

        RequestContext requestContext = new RequestContext(request);
        DynaActionForm f = (DynaActionForm) actionForm;

        ListHelper helper = new ListHelper(this, request);
        helper.setDataSetName(RequestContext.PAGE_LIST);
        helper.execute();

        Map<String, Object> params = new HashMap<>();
        params.put(RequestContext.MODE,
                requestContext.getRequiredParamAsString(RequestContext.MODE));

        if (request.getParameter("dispatch") != null) {
            String packagesDecl = request.getParameter("packagesDecl");
            if (requestContext.wasDispatched("installconfirm.jsp.confirm")) {
                return executePackageAction(actionMapping, actionForm, request, response);
            }
        }

        // Pre-populate the date picker
        DynaActionForm dynaForm = (DynaActionForm) actionForm;
        DatePicker picker = getStrutsDelegate().prepopulateDatePicker(request, dynaForm,
            "date", YEAR_RANGE_POSITIVE);
        request.setAttribute("date", picker);

        // Pre-populate the Action Chain selector
        ActionChainHelper.prepopulateActionChains(request);

        Set<Long> systemIds = new HashSet<>(SsmManager.listServerIds(requestContext.getCurrentUser()));
        populateMaintenanceWindows(request, systemIds);

        return actionMapping.findForward(RhnHelper.DEFAULT_FORWARD);
    }

    /** {@inheritDoc} */
    @Override
    public List getResult(RequestContext context) {

        HttpServletRequest request = context.getRequest();
        User user = context.getCurrentUser();

        // Stuff packages into an RhnSet to be used in the query
        String packagesDecl = (String) request.getAttribute("packagesDecl");
        if (packagesDecl != null) {
            Set<String> data = SessionSetHelper.lookupAndBind(request, packagesDecl);

            // bz465892 - As the selected packages are parsed, remove duplicates
            // keeping the highest EVR
            // bz594455 - highest EVR per package name does not work. What if we have
            // different base channels in same ssm, like maybe rhel 5 and rhel 6? What
            // if the highest EVR package is only available in a child channel that
            // one server is subscribed to but not another? We need to find the highest
            // EVR for each combination of package_name:package_arch:system and keep
            // all of those. The addition of system makes the combinatorics much more
            // complicated, especially since that's not information we have (and it
            // would be unreasonable to add) in the request attributes. We have to
            // push the selected packages down to the database and then have a query
            // that returns the system to package map.

            RhnSet packageSet = RhnSetManager.createSet(user.getId(),
                RhnSetDecl.SSM_UPGRADE_PACKAGES_LIST.getLabel(), SetCleanup.NOOP);

            for (String idCombo : data) {
                PackageListItem item = PackageListItem.parse(idCombo);
                packageSet.addElement(item.getIdOne(), item.getIdTwo(), item.getIdThree());
            }

            RhnSetManager.store(packageSet);
        }

        DataResult results = SystemManager.ssmSystemPackagesToUpgrade(user,
            RhnSetDecl.SSM_UPGRADE_PACKAGES_LIST.getLabel());

        TagHelper.bindElaboratorTo("groupList", results.getElaborator(), request);

        return results;
    }

    private ActionForward executePackageAction(ActionMapping mapping,
                                               ActionForm formIn,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {

        RequestContext context = new RequestContext(request);
        StrutsDelegate strutsDelegate = getStrutsDelegate();
        User user = context.getCurrentUser();

        // Is taskomatic running?
        if (!TASKOMATIC_API.isRunning()) {
            log.error("Cannot schedule action: Taskomatic is not running");
            ActionErrors errors = new ActionErrors();
            getStrutsDelegate().addError("taskscheduler.down", errors);
            getStrutsDelegate().saveMessages(request, errors);
            return mapping.findForward(RhnHelper.CONFIRM_FORWARD);
        }

        // Load the date selected by the user
        DynaActionForm form = (DynaActionForm) formIn;
        Date earliest = getStrutsDelegate().readScheduleDate(form, "date", YEAR_RANGE_POSITIVE);

        // Load the Action Chain, if any
        ActionChain actionChain = ActionChainHelper.readActionChain(form, user);

        log.debug("Getting package upgrade data.");
        List<Map> result =  getResult(context);
        ((DataResult) result).elaborate();


        Map<Long, List<Map<String, Long>>> sysPackageSet =
                new HashMap<>();
        for (Map sys : result) {
            Long sysId = (Long) sys.get("id");
            List<Map<String, Long>> pkgSet = new ArrayList<>();
            sysPackageSet.put(sysId, pkgSet);
            for (Map pkg : (List<Map>) sys.get("elaborator0")) {
                Map<String, Long> newPkg = new HashMap();
                newPkg.put("name_id", (Long) pkg.get("name_id"));
                newPkg.put("evr_id", (Long) pkg.get("evr_id"));
                newPkg.put("arch_id", (Long) pkg.get("arch_id"));
                pkgSet.add(newPkg);
            }

        }


        log.debug("Publishing schedule package upgrade event to message queue.");
        SsmUpgradePackagesEvent event = new SsmUpgradePackagesEvent(user.getId(), earliest,
            actionChain, sysPackageSet);
        MessageQueue.publish(event);

        // Remove the packages from session and the DB
        SessionSetHelper.obliterate(request, request.getParameter("packagesDecl"));

        log.debug("Deleting set.");
        RhnSetManager.deleteByLabel(user.getId(),
            RhnSetDecl.SSM_UPGRADE_PACKAGES_LIST.getLabel());

        ActionMessages msgs = new ActionMessages();

        msgs.add(ActionMessages.GLOBAL_MESSAGE,
            new ActionMessage("ssm.package.upgrade.message.packageupgrade"));
        strutsDelegate.saveMessages(request, msgs);

        return mapping.findForward(RhnHelper.CONFIRM_FORWARD);


    }

    @Override
    public void populateMaintenanceWindows(HttpServletRequest request, Set<Long> systemIds) {
        if (ActionFactory.TYPE_PACKAGES_UPDATE.isMaintenancemodeOnly()) {
            MaintenanceWindowHelper.prepopulateMaintenanceWindows(request, systemIds);
        }
    }
}
