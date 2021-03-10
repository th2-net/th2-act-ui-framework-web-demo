/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.act;

import com.exactpro.th2.act.actions.ExtractMessage;
import com.exactpro.th2.act.actions.SendNewOrderSingle;
import com.exactpro.th2.act.framework.TestUIFramework;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.grpc.HandWinActGrpc;
import com.exactpro.th2.act.grpc.NewOrderSingleParams;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.RptViewerDetails;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.act.grpc.hand.RhTargetServer;
import com.exactpro.th2.check1.grpc.Check1Service;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandWinAct extends HandWinActGrpc.HandWinActImplBase
{
	private static final Logger logger = LoggerFactory.getLogger(HandWinAct.class);
	private final TestUIFramework framework; 
	private final Check1Service verifierConnector;
	
	public HandWinAct(Check1Service verifierConnector, TestUIFramework framework)
	{
		this.verifierConnector = verifierConnector;
		this.framework = framework;
	}

	@Override
	public void register(RhTargetServer request, StreamObserver<RhSessionID> responseObserver)
	{
		RhSessionID result = framework.getHandExecutor().register(request);
		
		try {
			framework.registerSession(result);
			responseObserver.onNext(result);
		} catch (UIFrameworkException e) {
			logger.error("Cannot register framework session", e);
			responseObserver.onError(e);
		}
		
		responseObserver.onCompleted();
	}
	

	@Override
	public void unregister(RhSessionID request, StreamObserver<Empty> responseObserver)
	{
		framework.getHandExecutor().unregister(request);
		
		try {
			framework.unregisterSession(request);
			responseObserver.onNext(Empty.newBuilder().build());
		} catch (UIFrameworkException e) {
			logger.error("Cannot unregister framework session", e);
			responseObserver.onError(e);
		}
		
		responseObserver.onCompleted();
	}


	@Override
	public void sendNewOrderSingle(NewOrderSingleParams request, StreamObserver<RhBatchResponseDemo> responseObserver) {
		new SendNewOrderSingle(framework, verifierConnector, responseObserver).run(request);
	}

	@Override
	public void extractSentMessage(RptViewerDetails request, StreamObserver<RhBatchResponseDemo> responseObserver) {
		new ExtractMessage(framework, responseObserver).run(request);
	}
}
