/*
 * Copyright (C) 2019 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.briefcase.ui.reused.transfer.sourcetarget.source;

import static java.awt.Cursor.HAND_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;
import static java.util.stream.Collectors.toList;
import static javax.swing.SwingUtilities.invokeLater;
import static org.opendatakit.briefcase.ui.reused.SwingUtils.uncheckedBrowse;
import static org.opendatakit.briefcase.ui.reused.UI.removeAllMouseListeners;

import java.awt.Container;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;
import org.bushe.swing.event.EventBus;
import org.opendatakit.briefcase.model.BriefcasePreferences;
import org.opendatakit.briefcase.model.FormStatus;
import org.opendatakit.briefcase.pull.PullEvent;
import org.opendatakit.briefcase.pull.central.PullFromCentral;
import org.opendatakit.briefcase.reused.BriefcaseException;
import org.opendatakit.briefcase.reused.http.Http;
import org.opendatakit.briefcase.reused.job.JobsRunner;
import org.opendatakit.briefcase.reused.transfer.CentralServer;
import org.opendatakit.briefcase.reused.transfer.RemoteServer.Test;
import org.opendatakit.briefcase.transfer.TransferForms;
import org.opendatakit.briefcase.ui.reused.MouseAdapterBuilder;
import org.opendatakit.briefcase.ui.reused.transfer.sourcetarget.CentralServerDialog;

/**
 * Represents an ODK Central server as a source of forms for the Pull UI Panel.
 */
public class Central implements PullSource<CentralServer> {
  private final Http http;
  private final Test<CentralServer> serverTester;
  private final Consumer<PullSource> onSourceCallback;
  private CentralServer server;

  Central(Http http, Test<CentralServer> serverTester, Consumer<PullSource> onSourceCallback) {
    this.http = http;
    this.serverTester = serverTester;
    this.onSourceCallback = onSourceCallback;
  }

  @Override
  public List<FormStatus> getFormList() {
    String token = http.execute(server.getSessionTokenRequest())
        .orElseThrow(() -> new BriefcaseException("Can't authenticate with ODK Central"));

    return http.execute(server.getFormsListRequest(token))
        .map(formDefs -> formDefs.stream().map(FormStatus::new).collect(toList()))
        .orElseThrow(() -> new BriefcaseException("Can't get forms list from server"));
  }

  @Override
  public void storeSourcePrefs(BriefcasePreferences prefs, boolean storePasswords) {
    server.storeInPrefs(prefs, storePasswords);
  }

  @Override
  public String getDescription() {
    return server.getBaseUrl().toString();
  }

  @Override
  public void onSelect(Container container) {
    CentralServerDialog dialog = CentralServerDialog.empty(serverTester);
    dialog.onConnect(this::set);
    dialog.getForm().setVisible(true);
  }

  @Override
  public void decorate(JLabel label) {
    label.setText("<html>URL: <a href=\"" + server.getBaseUrl().toString() + "\">" + getDescription() + "</a><br/>Project ID: " + server.getProjectId() + "</html>");
    label.setCursor(getPredefinedCursor(HAND_CURSOR));
    removeAllMouseListeners(label);
    label.addMouseListener(new MouseAdapterBuilder()
        .onClick(__ -> invokeLater(() -> uncheckedBrowse(server.getBaseUrl())))
        .build());
  }

  @Override
  public boolean canBeReloaded() {
    return true;
  }

  @Override
  public boolean accepts(Object o) {
    return o instanceof CentralServer;
  }

  @Override
  public void set(CentralServer server) {
    this.server = server;
    onSourceCallback.accept(this);
  }

  @Override
  public String toString() {
    return "Central server";
  }

  @Override
  public JobsRunner pull(TransferForms forms, BriefcasePreferences appPreferences, BriefcasePreferences localPreferences) {
    String token = http.execute(server.getSessionTokenRequest()).orElseThrow(() -> new BriefcaseException("Can't authenticate with ODK Central"));
    PullFromCentral pullOp = new PullFromCentral(
        http,
        server,
        appPreferences.getBriefcaseDir().orElseThrow(BriefcaseException::new),
        token,
        EventBus::publish
    );

    return JobsRunner
        .launchAsync(forms.map(pullOp::pull))
        .onComplete(() -> EventBus.publish(new PullEvent.PullComplete()));
  }

}
