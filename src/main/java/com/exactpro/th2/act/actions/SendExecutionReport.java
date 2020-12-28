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

package com.exactpro.th2.act.actions;

import com.exactpro.th2.act.ActResult;
import com.exactpro.th2.act.framework.UIFramework;
import com.exactpro.th2.act.framework.UIFrameworkContext;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.grpc.ExecutionReportParams;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.hand.RhAction;
import com.exactpro.th2.act.grpc.hand.RhBatchResponse;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.act.grpc.hand.rhactions.RhActionsMessages;
import com.exactpro.th2.check1.grpc.Check1Service;
import com.exactpro.th2.check1.grpc.CheckpointRequest;
import com.exactpro.th2.check1.grpc.CheckpointResponse;
import com.exactpro.th2.common.grpc.Checkpoint;
import com.exactpro.th2.common.grpc.EventID;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.exactpro.th2.act.grpc.hand.rhactions.RhActionsMessages.Locator.CSS_SELECTOR;
import static com.google.protobuf.TextFormat.shortDebugString;

public class SendExecutionReport extends ActAction<ExecutionReportParams>
{
	public static final String URL = "http://10.44.17.215:9001/index.html";
	private static final Logger logger = LoggerFactory.getLogger(SendExecutionReport.class);

	private final StreamObserver<RhBatchResponseDemo> responseObserver;
	private final Check1Service verifierConnector;
	private Checkpoint checkpoint;

	public SendExecutionReport(UIFramework framework, Check1Service verifierConnector, StreamObserver<RhBatchResponseDemo> responseObserver)
	{
		super(framework);
		this.responseObserver = responseObserver;
		this.verifierConnector = verifierConnector;
	}

	@Override
	protected String getName()
	{
		String[] splitted = StringUtils.splitByCharacterTypeCamelCase(getClass().getSimpleName());
		return StringUtils.join(splitted, " ");
	}

	@Override
	protected Map<String, String> convertRequestParams(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getAllFields().entrySet()
			.stream().filter(e -> e.getValue() instanceof String)
			.collect(Collectors.toMap(e -> e.getKey().getName(), e -> (String) e.getValue()));
	}

	@Override
	protected RhSessionID getSessionID(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getSessionID();
	}

	@Override
	protected EventID getParentEventId(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getEventID();
	}

	@Override
	protected Logger getLogger()
	{
		return logger;
	}

	@Override
	protected void collectActions(ExecutionReportParams executionReportParams, UIFrameworkContext uiFrameworkContext,
			ActResult actResult) throws UIFrameworkException
	{
		// #action,#url
		// Open,http://10.44.17.215:9001/index.html
		RhActionsMessages.Open openMsg = RhActionsMessages.Open.newBuilder().setUrl(URL).build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setOpen(openMsg).build());

		// #action,#seconds
		// Wait,3
		RhActionsMessages.Wait wait3Msg = RhActionsMessages.Wait.newBuilder().setSeconds(3).build();
		RhAction wait3Action = RhAction.newBuilder().setWait(wait3Msg).build();
		uiFrameworkContext.addRhAction(wait3Action);

		// #action,#wait,#locator,#matcher,#text
		// SendKeys,5,cssSelector,#session,%Session%
		RhActionsMessages.SendKeys skMsg = RhActionsMessages.SendKeys.newBuilder()
				.setLocator(CSS_SELECTOR)
				.setNeedClick(true)
				.setWait(5)
				.setMatcher("#session")
				.setText(executionReportParams.getSession())
				.build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setSendKeys(skMsg).build());

		
		// #action,#wait,#locator,#matcher,#text
		// SendKeys,5,cssSelector,#msg-type,ExecutionReport
		skMsg = RhActionsMessages.SendKeys.newBuilder()
				.setLocator(CSS_SELECTOR)
				.setNeedClick(true)
				.setWait(5)
				.setMatcher("#msg-type")
				.setText(executionReportParams.getMessageType()+"#Enter")
				.build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setSendKeys(skMsg).build());

		// #action,#seconds
		// Wait,3
		uiFrameworkContext.addRhAction(wait3Action);

		// #action,#wait,#locator,#matcher,#text
		// SendKeys,5,cssSelector,.inputarea,"#down##end##backspace#%ExecID%,"
		skMsg = RhActionsMessages.SendKeys.newBuilder()
				.setLocator(CSS_SELECTOR)
				.setNeedClick(true)
				.setWait(5)
				.setMatcher(".inputarea")
				.setText(fillFields(executionReportParams))
				.build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setSendKeys(skMsg).build());
		
		// #action,#wait,#locator,#matcher,
		// Click,5,cssSelector,div.button:nth-child(2)
		RhActionsMessages.Click clickMsg = RhActionsMessages.Click.newBuilder()
				.setLocator(CSS_SELECTOR)
				.setWait(5)
				.setMatcher("div.button:nth-child(2")
				.build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setClick(clickMsg).build());

		
		RhActionsMessages.GetElementInnerHtml getElementMsg = RhActionsMessages.GetElementInnerHtml.newBuilder()
				.setLocator(CSS_SELECTOR)
				.setWait(5)
				.setMatcher("div.result-table")
				.build();
		uiFrameworkContext.addRhAction(RhAction.newBuilder().setGetElementInnerHtml(getElementMsg).build());

		// #action,#seconds
		// Wait,10
		RhActionsMessages.Wait wait10Msg = RhActionsMessages.Wait.newBuilder().setSeconds(10).build();
		RhAction wait10Action = RhAction.newBuilder().setWait(wait10Msg).build();
		uiFrameworkContext.addRhAction(wait10Action);
		
	}

	private String fillFields(ExecutionReportParams params)
	{
		Map<String, String> fields = convertRequestParams(params);
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> entry : getParamsPositions().entrySet())
		{
			String fieldValue = fields.getOrDefault(entry.getKey(), "");
			Integer relativePosition = entry.getValue();
			sb.append(StringUtils.repeat("#down#", relativePosition)).append("#end##left#")
					.append(fieldValue.isEmpty() ? fieldValue : StringUtils.wrap(fieldValue,'"')).append("#right#");
		}

		return sb.toString();
	}

	private Map<String, Integer> getParamsPositions()
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		result.put("BeginString", 2);
		result.put("BodyLength", 1);
		result.put("SenderCompID", 2);
		result.put("TargetCompID", 1);
		result.put("MsgSeqNum", 1);
		result.put("ExecID", 2);
		result.put("ClOrdID", 1);
		result.put("OrderID", 1);
		result.put("LeavesQty", 3);
		result.put("CumQty", 1);
		result.put("SecurityID", 1);
		result.put("PartyID", 5);
		result.put("OrderQty", 8);
		result.put("TransactTime", 1);
		result.put("CheckSum", 2);

		return result;
	}

	@Override
	public void run(ExecutionReportParams details)
	{

		RhSessionID sessionID = getSessionID(details);

		ActResult actResult = new ActResult();
		UIFrameworkContext frameworkContext = null;
		try {
			frameworkContext = framework.newExecution(sessionID);
			EventID currentEventId = getParentEventId(details);
			if (storeParentEvent()) {
				Map<String, String> requestParams = convertRequestParams(details);
				currentEventId = framework.createParentEvent(currentEventId, getName(), requestParams);
			}
			frameworkContext.setParentEventId(currentEventId);
			
//			this.collectActions(details, frameworkContext, actResult);
//			this.submitActions(details, frameworkContext, actResult);
			checkpoint = registerCheckPoint(currentEventId);
			this.collectActions(details, frameworkContext, actResult);
			this.submitActions(details, frameworkContext, actResult);
			actResult.setSessionID(sessionID);

		} catch (UIFrameworkException e) {
			logger.error("Cannot execute", e);
			actResult.setScriptStatus(ActResult.ActExecutionStatus.ACT_ERROR);
			actResult.setErrorInfo("Cannot unregister framework session:" + e.getMessage());
		} finally {
			if (frameworkContext != null) {
				framework.onExecutionFinished(frameworkContext);
			}
		}

		try {
			this.processResult(actResult);
		} catch (UIFrameworkException e) {
			logger.error("Cannot process act result", e);
		}
	}

	protected static RhBatchResponse.ScriptExecutionStatus convertStatus(ActResult.ActExecutionStatus actStatus)
	{
		switch (actStatus)
		{
			case SUCCESS:
				return RhBatchResponse.ScriptExecutionStatus.SUCCESS;
			case COMPILE_ERROR:
				return RhBatchResponse.ScriptExecutionStatus.COMPILE_ERROR;
			case EXECUTION_ERROR:
				return RhBatchResponse.ScriptExecutionStatus.EXECUTION_ERROR;
			case UNKNOWN_ERROR:
			case ACT_ERROR:
			case HAND_ERROR:
			default:
				return RhBatchResponse.ScriptExecutionStatus.UNRECOGNIZED;
		}
	}

	@Override
	protected void processResult(ActResult actResult) throws UIFrameworkException
	{
		RhBatchResponse.Builder rhBatchResponseBuilder = RhBatchResponse.newBuilder()
				.setScriptStatus(convertStatus(actResult.getScriptStatus()))
				.setSessionId(actResult.getSessionID().toString());
		if (actResult.getErrorInfo() != null)
			rhBatchResponseBuilder.setErrorMessage(actResult.getErrorInfo());
		RhBatchResponse rhBatchResponse = rhBatchResponseBuilder.build();

		RhBatchResponseDemo response = RhBatchResponseDemo.newBuilder()
				.setBatchResponse(rhBatchResponse)
				.setCheckpoint(checkpoint)
				.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	private Checkpoint registerCheckPoint(EventID parentEventId) {
		logger.debug("Registering the checkpoint started");
		CheckpointResponse response = verifierConnector.createCheckpoint(CheckpointRequest.newBuilder()
				.setParentEventId(parentEventId)
				.build());
		if (logger.isDebugEnabled()) {
			logger.debug("Registering the checkpoint ended. Response " + shortDebugString(response));
		}
		return response.getCheckpoint();
	}

	@Override
	protected String getStatusInfo()
	{
		return "status info";
	}
}
