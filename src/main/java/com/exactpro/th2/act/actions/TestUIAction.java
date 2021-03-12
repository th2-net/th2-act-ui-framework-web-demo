/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.act.actions;

import com.exactpro.th2.act.ActResult;
import com.exactpro.th2.act.framework.TestUIFramework;
import com.exactpro.th2.act.framework.TestUIFrameworkContext;
import com.exactpro.th2.act.framework.TestUIFrameworkSessionContext;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.common.grpc.Checkpoint;
import io.grpc.stub.StreamObserver;

public abstract class TestUIAction<T> extends ActAction<T, TestUIFrameworkContext, TestUIFrameworkSessionContext> {

	protected final StreamObserver<RhBatchResponseDemo> responseObserver;
	
	public TestUIAction(TestUIFramework framework, StreamObserver<RhBatchResponseDemo> responseObserver) {
		super(framework);
		this.responseObserver = responseObserver;
	}

	protected Checkpoint getCheckpoint() {
		return null;
	}
	
	@Override
	protected void processResult(ActResult actResult) throws UIFrameworkException
	{
		var response = RhBatchResponseDemo.newBuilder();
		if (actResult.getData() != null) {
			response.putAllData(actResult.getData());
		}
		if (actResult.getStatusInfo() != null) {
			response.setStatusInfo(actResult.getStatusInfo());
		}
		if (actResult.getErrorInfo() != null) {
			response.setErrorInfo(actResult.getErrorInfo());
		}
		if (actResult.getScriptStatus() != null) {
			response.setScriptStatus(SendNewOrderSingle.convertStatus(actResult.getScriptStatus()));
		} else {
			response.setScriptStatus(RhBatchResponseDemo.ExecutionStatus.SUCCESS);
		}
		var checkpoint = getCheckpoint();
		if (checkpoint != null) {
			response.setCheckpoint(checkpoint);
		}

		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	protected static RhBatchResponseDemo.ExecutionStatus convertStatus(ActResult.ActExecutionStatus actStatus)
	{
		switch (actStatus)
		{
			case SUCCESS:
				return RhBatchResponseDemo.ExecutionStatus.SUCCESS;
			case COMPILE_ERROR:
				return RhBatchResponseDemo.ExecutionStatus.COMPILE_ERROR;
			case EXECUTION_ERROR:
				return RhBatchResponseDemo.ExecutionStatus.EXECUTION_ERROR;
			case HAND_ERROR:
				return RhBatchResponseDemo.ExecutionStatus.HAND_ERROR;
			case ACT_ERROR:
			default:
				return RhBatchResponseDemo.ExecutionStatus.ACT_ERROR;
		}
	}
}
