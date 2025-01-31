/*
 * Copyright (c) 2009--2015 Red Hat, Inc.
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
package com.redhat.rhn.frontend.action.kickstart;

import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.domain.common.FileList;
import com.redhat.rhn.domain.rhnset.RhnSetElement;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.manager.kickstart.FilePreservationListsCommand;
import com.redhat.rhn.manager.kickstart.KickstartLister;
import com.redhat.rhn.manager.rhnset.RhnSetDecl;

import org.apache.struts.action.ActionForm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * KickstartPreservationListSubmitAction.
 */
public class KickstartPreservationListSubmitAction extends BaseKickstartListSubmitAction {

    public static final String UPDATE_METHOD = "kickstart.filelists.jsp.submit";

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataResult getDataResult(User userIn,
                                       ActionForm formIn,
                                       HttpServletRequest request) {
        return KickstartLister.getInstance().preservationListsInOrg(
                userIn.getOrg(), null);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void operateOnRemovedElements(List<RhnSetElement> elements,
                                            HttpServletRequest request) {
        RequestContext ctx = new RequestContext(request);

        FilePreservationListsCommand cmd =
            new FilePreservationListsCommand(
                    ctx.getRequiredParam(RequestContext.KICKSTART_ID),
                    ctx.getCurrentUser());

        ArrayList<Long> ids = new ArrayList<>();

        for (RhnSetElement element : elements) {
            ids.add(element.getElement());
        }

        cmd.removeFileListsByIds(ids);
        cmd.store();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void operateOnAddedElements(List<RhnSetElement> elements,
                                          HttpServletRequest request) {
        RequestContext ctx = new RequestContext(request);

        FilePreservationListsCommand cmd =
            new FilePreservationListsCommand(
                    ctx.getRequiredParam(RequestContext.KICKSTART_ID),
                    ctx.getCurrentUser());

        ArrayList<Long> ids = new ArrayList<>();

        for (RhnSetElement element : elements) {
            ids.add(element.getElement());
        }

        cmd.addFileListsByIds(ids);
        cmd.store();
    }

    /**
     *
     * @return security label for activation keys
     */
    @Override
    public RhnSetDecl getSetDecl() {
        return RhnSetDecl.FILE_LISTS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processMethodKeys(Map<String, String> map) {
        map.put(UPDATE_METHOD, "operateOnDiff");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Iterator<FileList> getCurrentItemsIterator(RequestContext ctx) {
        FilePreservationListsCommand cmd =
            new FilePreservationListsCommand(
                    ctx.getRequiredParam(RequestContext.KICKSTART_ID),
                    ctx.getCurrentUser());
        return cmd.getPreserveFileLists().iterator();
    }

}
