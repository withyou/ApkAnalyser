/*
 * Copyright (C) 2012 Sony Mobile Communications AB
 *
 * This file is part of ApkAnalyser.
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

package andreflect.gui.action.injection;

import java.awt.event.ActionEvent;
import java.util.Iterator;

import javax.swing.Icon;

import jerl.bcm.inj.Injection;
import mereflect.MEMethod;

import org.jf.dexlib.CodeItem;
import org.jf.dexlib.FieldIdItem;
import org.jf.dexlib.Code.Instruction;
import org.jf.dexlib.Code.InstructionWithReference;

import analyser.gui.MainFrame;
import analyser.gui.Selection;
import analyser.gui.TextBuilder;
import analyser.gui.actions.bytecodemod.AbstractTreeBytecodeModAction;
import analyser.logic.BytecodeModificationMediator;
import analyser.logic.RefContext;
import analyser.logic.RefField;
import analyser.logic.RefFieldAccess;
import analyser.logic.RefMethod;
import analyser.logic.Reference;
import andreflect.ApkClassContext;
import andreflect.DexField;
import andreflect.DexMethod;
import andreflect.Util;
import andreflect.injection.impl.DalvikMethodField;

public class DalvikMethodFieldWriteAction extends AbstractTreeBytecodeModAction {

    private static final long serialVersionUID = -1273863284481378500L;
    protected static DalvikMethodFieldWriteAction m_inst = null;
    protected static DalvikMethodFieldWriteAction m_inst_onefield = null;
    int traverseCount, traverseIndex = 0;
    FieldIdItem fieldIdItem;

    public static DalvikMethodFieldWriteAction getInstance(MainFrame mainFrame)
    {
        if (m_inst == null)
        {
            m_inst = new DalvikMethodFieldWriteAction("Print writing all fields", null);
            m_inst.setMainFrame(mainFrame);
        }
        return m_inst;
    }

    public static DalvikMethodFieldWriteAction getInstanceOneField(MainFrame mainFrame)
    {
        if (m_inst_onefield == null)
        {
            m_inst_onefield = new DalvikMethodFieldWriteAction("Print writing this field", null);
            m_inst_onefield.setMainFrame(mainFrame);
        }
        return m_inst_onefield;
    }

    protected DalvikMethodFieldWriteAction(String arg0, Icon arg1)
    {
        super(arg0, arg1);
    }

    @Override
    public void run(ActionEvent e) throws Throwable {
        if (Selection.getSelectedView() instanceof TextBuilder) {
        } else {
            Object ref = Selection.getSelectedObject();
            if (ref != null && ref instanceof RefField) {
                RefField rf = (RefField) ref;
                Iterator<Reference> i = rf.getChildren().iterator();
                while (i.hasNext()) {
                    RefFieldAccess access = (RefFieldAccess) i.next();
                    fieldIdItem = access.getAccess().fieldIdItem;
                    if (access.getAccess().isRead == false) {
                        createInjection(access.getAccess().method);
                    }
                }
                return;
            }
        }
        super.run(e);
    }

    @Override
    protected void modify(MEMethod method) throws Throwable {
        fieldIdItem = Util.getFieldIdItem();

        if (fieldIdItem == null) {
            if (method.isAbstract()) {
                return;
            }
            DexMethod dexMethod = (DexMethod) method;
            if (hasWriteField(dexMethod, fieldIdItem)) {
                createInjection(dexMethod);
            }
        } else {
            Object ref = Selection.getSelectedView();
            TextBuilder tb = (TextBuilder) ref;
            Object lineRef = tb.getLineBuilder().getReference(tb.getCurrentLine());
            ApkClassContext apkContext = (ApkClassContext) (((DexField) lineRef).getDexClass().getResource().getContext());
            Iterator<Reference> i = MainFrame.getInstance().getResolver().getMidletResources().iterator();
            RefContext refContext = null;
            while (i.hasNext()) {
                Object obj = i.next();
                if (obj instanceof RefContext
                        && ((RefContext) obj).getContext() == apkContext) {
                    refContext = (RefContext) obj;
                    break;
                }
            }

            if (refContext != null) {
                traverseCount = 0;
                traverseIndex = 0;
                getTraverseCount(refContext);
                if (traverseCount != 0) {
                    traverseInside(refContext);
                }
            }
        }
    }

    private void createInjection(DexMethod method) {
        DalvikMethodField fieldInjection = new DalvikMethodField(getMethodSignature(method),
                method.getMEClass().getName() + ":" + getMethodSignature(method),
                fieldIdItem,
                false);
        BytecodeModificationMediator.getInstance().registerModification(
                method.getMEClass().getResource().getContext(),
                method.getMEClass(),
                fieldInjection,
                method);

        ((MainFrame) getMainFrame()).getMidletTree().findAndMarkNode(method, Reference.MODIFIED);
    }

    protected void getTraverseCount(Reference ref) throws Throwable {
        if (ref instanceof RefMethod) {
            traverseCount++;
        } else {
            Iterator<Reference> i = ref.getChildren().iterator();
            while (i.hasNext()) {
                getTraverseCount(i.next());
            }
        }
    }

    protected void traverseInside(Reference ref) throws Throwable {
        if (ref instanceof RefMethod) {
            if (hasWriteField((DexMethod) ((RefMethod) ref).getMethod(), fieldIdItem)) {
                createInjection((DexMethod) ((RefMethod) ref).getMethod());
            }
            getMainFrame().actionReportWork(this, 100 * traverseIndex++ / traverseCount);
        } else {
            Iterator<Reference> i = ref.getChildren().iterator();
            while (i.hasNext()) {
                traverseInside(i.next());
            }
        }
    }

    @Override
    protected Injection getInjection(String className, String methodSignature) {
        //not used
        return null;
    }

    public boolean hasWriteField(DexMethod method, FieldIdItem fieldIdItem) {
        boolean ret = false;
        CodeItem codeItem = method.getEncodedMethod().codeItem;
        if (codeItem != null) {
            Instruction[] instructions = method.getEncodedMethod().codeItem.getInstructions();
            for (Instruction instruction : instructions) {
                switch (instruction.deodexedInstruction.opcode) {
                case IPUT:
                case IPUT_WIDE:
                case IPUT_OBJECT:
                case IPUT_BOOLEAN:
                case IPUT_BYTE:
                case IPUT_CHAR:
                case IPUT_SHORT:
                case SPUT:
                case SPUT_WIDE:
                case SPUT_OBJECT:
                case SPUT_BOOLEAN:
                case SPUT_BYTE:
                case SPUT_CHAR:
                case SPUT_SHORT:
                    if (fieldIdItem == null
                            || ((InstructionWithReference) instruction).getReferencedItem() == fieldIdItem) {
                        ret = true;
                    }
                    break;
                /*
                case IPUT_QUICK:
                case IPUT_WIDE_QUICK:
                case IPUT_OBJECT_QUICK:
                //for gingerbread
                case IPUT_VOLATILE:
                case IPUT_WIDE_VOLATILE:
                case IPUT_OBJECT_VOLATILE:
                case SPUT_VOLATILE:
                case SPUT_WIDE_VOLATILE:
                case SPUT_OBJECT_VOLATILE:
                Instruction deodexedIns = method.getDeodexedInstruction(instruction);
                if (fieldIdItem == null
                		||((InstructionWithReference)deodexedIns).getReferencedItem() == fieldIdItem){
                	ret  = true;
                }
                break;
                 */
                }

                if (ret == true) {
                    break;
                }
            }
        }
        return ret;
    }

}
