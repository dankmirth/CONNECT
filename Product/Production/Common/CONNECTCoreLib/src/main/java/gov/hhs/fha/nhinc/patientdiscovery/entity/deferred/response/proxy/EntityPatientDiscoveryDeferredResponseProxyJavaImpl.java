/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *  
 * Copyright 2010(Year date of delivery) United States Government, as represented by the Secretary of Health and Human Services.  All rights reserved.
 *  
 */
package gov.hhs.fha.nhinc.patientdiscovery.entity.deferred.response.proxy;

import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetCommunitiesType;
import gov.hhs.fha.nhinc.patientdiscovery.entity.deferred.response.EntityPatientDiscoveryDeferredResponseOrch;
import gov.hhs.fha.nhinc.patientdiscovery.entity.deferred.response.EntityPatientDiscoveryDeferredResponseOrchFactory;
import gov.hhs.fha.nhinc.patientdiscovery.entity.deferred.response.EntityPatientDiscoveryDeferredResponseOrchImpl;
import gov.hhs.fha.nhinc.patientdiscovery.nhin.GenericFactory;

import org.hl7.v3.MCCIIN000002UV01;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.v3.PRPAIN201306UV02;

/**
 *
 * @author dunnek
 */
public class EntityPatientDiscoveryDeferredResponseProxyJavaImpl implements EntityPatientDiscoveryDeferredResponseProxy
{

    private static Log log = null;
    
    private GenericFactory<EntityPatientDiscoveryDeferredResponseOrch> orchestrationFactory;

    public EntityPatientDiscoveryDeferredResponseProxyJavaImpl()
    {
        log = createLogger();
        orchestrationFactory = new EntityPatientDiscoveryDeferredResponseOrchFactory();
        
    }

    protected Log createLogger()
    {
        return LogFactory.getLog(getClass());
    }

    public MCCIIN000002UV01 processPatientDiscoveryAsyncResp(PRPAIN201306UV02 body, AssertionType assertion, NhinTargetCommunitiesType target)
    {
        log.debug("Begin EntityPatientDiscoveryDeferredResponseProxyJavaImpl.processPatientDiscoveryAsyncResp(...)");

        return getEntityImpl().processPatientDiscoveryAsyncRespOrch(body, assertion, target);

    }

    protected EntityPatientDiscoveryDeferredResponseOrch getEntityImpl()
    {
        return orchestrationFactory.create();
    }
}
