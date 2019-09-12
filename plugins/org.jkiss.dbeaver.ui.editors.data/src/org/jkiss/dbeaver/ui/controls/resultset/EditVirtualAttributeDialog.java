/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
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
package org.jkiss.dbeaver.ui.controls.resultset;

import org.apache.commons.jexl3.JexlExpression;
import org.eclipse.jface.fieldassist.ComboContentAdapter;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.virtual.DBVEntityAttribute;
import org.jkiss.dbeaver.model.virtual.DBVUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.BaseDialog;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom virtual attribute edit dialog
 */
class EditVirtualAttributeDialog extends BaseDialog {
    private final ResultSetViewer viewer;
    private final DBVEntityAttribute vAttr;
    private Text nameText;
    private Combo typeCombo;
    private Combo kindCombo;
    private Text expressionText;
    private Text previewText;

    public EditVirtualAttributeDialog(Shell parentShell, ResultSetViewer viewer, DBVEntityAttribute vAttr) {
        super(parentShell, "Add virtual column", DBIcon.TREE_COLUMN);
        this.viewer = viewer;
        this.vAttr = vAttr;
    }

    @Override
    protected Composite createDialogArea(Composite parent) {
        Composite dialogArea = super.createDialogArea(parent);

        DBPDataSource dataSource = vAttr.getEntity().getDataSource();

        Composite panel = UIUtils.createComposite(dialogArea, 2);
        panel.setLayoutData(new GridData(GridData.FILL_BOTH));
        String name = vAttr.getName();
        int index = 1;
        for (;;) {
            DBVEntityAttribute vAttr2 = vAttr.getEntity().getVirtualAttribute(name);
            if (vAttr2 == null || vAttr2 == vAttr) {
                break;
            }
            index++;
            name = vAttr.getName() + index;
        }
        nameText = UIUtils.createLabelText(panel, "Column Name", name);
        typeCombo = UIUtils.createLabelCombo(panel, "Type Name", "Colmn type name", SWT.BORDER | SWT.DROP_DOWN);

        {
            DBPDataTypeProvider dataTypeProvider = DBUtils.getAdapter(DBPDataTypeProvider.class, dataSource);
            if (dataTypeProvider != null) {
                List<DBSDataType> localDataTypes = new ArrayList<>(dataTypeProvider.getLocalDataTypes());
                localDataTypes.sort(Comparator.comparing(DBSDataType::getFullTypeName));
                for (DBSDataType dataType : localDataTypes) {
                    typeCombo.add(dataType.getFullTypeName());
                }
                String defTypeName = vAttr.getTypeName();
                if (CommonUtils.isEmpty(defTypeName)) {
                    defTypeName = dataTypeProvider.getDefaultDataTypeName(DBPDataKind.STRING);
                }
                if (!CommonUtils.isEmpty(defTypeName)) {
                    typeCombo.setText(defTypeName);
                }
                typeCombo.addModifyListener(e -> {
                    DBSDataType dataType = dataTypeProvider.getLocalDataType(typeCombo.getText());
                    if (dataType != null) {
                        kindCombo.setText(dataType.getDataKind().name());
                    }
                });
            } else {
                typeCombo.setText(CommonUtils.notEmpty(vAttr.getTypeName()));
            }
            UIUtils.installContentProposal(
                typeCombo,
                new ComboContentAdapter(),
                new SimpleContentProposalProvider(typeCombo.getItems()));
        }

        kindCombo = UIUtils.createLabelCombo(panel, "Data Kind", "Column data kind", SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
        for (DBPDataKind dataKind : DBPDataKind.values()) {
            if (dataKind != DBPDataKind.UNKNOWN) {
                kindCombo.add(dataKind.name());
            }
        }
        kindCombo.setText(vAttr.getDataKind().name());

        expressionText = UIUtils.createLabelText(panel, "Expression", CommonUtils.notEmpty(vAttr.getExpression()), SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 300;
        gd.heightHint = expressionText.getLineHeight() * 5;
        expressionText.setLayoutData(gd);

        List<String> expressionProposals = new ArrayList<>();
        for (DBDAttributeBinding attr : viewer.getModel().getAttributes()) {
            expressionProposals.add(attr.getLabel());
        }

        UIUtils.installContentProposal(
            expressionText,
            new TextContentAdapter(),
            new SimpleContentProposalProvider(expressionProposals.toArray(new String[0])));

        previewText = UIUtils.createLabelText(panel, "Preview", "", SWT.BORDER | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        expressionText.addModifyListener(e -> generatePreviewValue());

        generatePreviewValue();

        return dialogArea;
    }

    private void generatePreviewValue() {
        String expression = expressionText.getText();

        ResultSetRow currentRow = viewer.getCurrentRow();
        if (currentRow == null) {
            previewText.setText("Select a row in data viewer to see expression results");
            return;
        }
        try {
            JexlExpression parsedExpression = DBVUtils.parseExpression(expression);
            Object result = DBVUtils.evaluateDataExpression(viewer.getModel().getAttributes(), currentRow.values, parsedExpression, nameText.getText());

            previewText.setText(CommonUtils.toString(result));
        } catch (Exception e) {
            previewText.setText(DBVUtils.getExpressionParseMessage(e));
        }
    }

    @Override
    protected void okPressed() {
        vAttr.setName(nameText.getText());
        vAttr.setTypeName(typeCombo.getText());
        vAttr.setDataKind(CommonUtils.valueOf(DBPDataKind.class, kindCombo.getText(), DBPDataKind.STRING));
        vAttr.setExpression(expressionText.getText());
        super.okPressed();
    }
}
