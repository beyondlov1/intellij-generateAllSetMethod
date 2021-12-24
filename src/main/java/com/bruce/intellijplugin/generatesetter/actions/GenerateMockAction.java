/*
 *  Copyright (c) 2017-2019, bruce.ge.
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU General Public License
 *    as published by the Free Software Foundation; version 2 of
 *    the License.
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *    GNU General Public License for more details.
 *    You should have received a copy of the GNU General Public License
 *    along with this program;
 */

package com.bruce.intellijplugin.generatesetter.actions;

import com.bruce.intellijplugin.generatesetter.CommonConstants;
import com.bruce.intellijplugin.generatesetter.GenerateAllHandlerAdapter;
import com.bruce.intellijplugin.generatesetter.GetInfo;
import com.bruce.intellijplugin.generatesetter.utils.PsiClassUtils;
import com.bruce.intellijplugin.generatesetter.utils.PsiDocumentUtils;
import com.bruce.intellijplugin.generatesetter.utils.PsiToolUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * @author bruce ge
 */
public class GenerateMockAction extends GenerateAllSetterBase {
    public GenerateMockAction() {
        super(new GenerateAllHandlerAdapter(){
            @Override
            public boolean isFromMethod() {
                return true; // todo
            }
        });
    }


    @NotNull
    @Override
    public String getText() {
        return CommonConstants.GENERATE_MOCK_FROM_CLASS;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        if(isAvailable(project,editor, element)){
            PsiClass psiClass = ((PsiClassType) ((PsiTypeElement) element.getPrevSibling().getFirstChild().getFirstChild()).getType()).resolve();
            mock(psiClass, project,element.getContainingFile(), element.getPrevSibling());
        }
    }

    private void mock(PsiClass psiClass, Project project, PsiFile psiFile, PsiElement element) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        Document document = psiDocumentManager.getDocument(psiFile);
        document.replaceString(element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(), mockOneClass(psiClass,"").getBlock());
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);
    }

    private MockItem mockOneClass(PsiClass psiClass, String lvlPrefix){
        StringBuilder sb = new StringBuilder();
        String varName = PsiToolUtils.lowerStart(Objects.requireNonNull(psiClass.getName()));
        List<PsiMethod> sourceGetMethods = PsiClassUtils.extractGetMethod(psiClass);
        GetInfo getInfo = null;
        if (sourceGetMethods.size() > 0) {
            getInfo = buildInfo(varName, sourceGetMethods);
        }

        if (getInfo != null){
            sb.append("\n");
            sb.append(psiClass.getName()).append(" ").append(varName).append(" = new ").append(psiClass.getName()).append("();");
            sb.append("\n");

            lvlPrefix = lvlPrefix + 0;
            for (PsiMethod method : sourceGetMethods) {
                String fieldName = getFieldNameByGetMethod(method);
                if (fieldName == null){
                    continue;
                }

                PsiType returnType = method.getReturnType();
                PsiClass returnClass = PsiTypesUtil.getPsiClass(returnType);
                String setterMethodName = "set" + PsiToolUtils.upperStart(fieldName);
                if (returnClass!= null && StringUtils.equalsIgnoreCase(returnClass.getName(), "list")){
                    String listVarName = PsiToolUtils.lowerStart(fieldName);
                    listVarName = listVarName + lvlPrefix;
                    sb.append(mockList(returnType, listVarName, lvlPrefix).getBlock());
                    sb.append(generateMethodWithParam(varName, setterMethodName, listVarName));
                } else {
                    String oneVarName = PsiToolUtils.lowerStart(fieldName);
                    oneVarName = oneVarName + lvlPrefix;
                    MockItem mockItem = mockOne(returnType, oneVarName,lvlPrefix);
                    sb.append(mockItem.getBlock());
                    sb.append(generateMethodWithParam(varName, setterMethodName, mockItem.getVarName()));
                }
            }
        }
        return new MockItem(varName, sb.toString());
    }

    private String getFieldNameByGetMethod(PsiMethod getMethod){
        if (getMethod.getName().startsWith(IS)) {
            return PsiToolUtils.lowerStart(getMethod.getName().substring(2));
        } else if (getMethod.getName().startsWith(GET)) {
            return PsiToolUtils.lowerStart(getMethod.getName().substring(3));
        }
        return null;
    }

    private MockItem mockOne(PsiType oneType, String oneVarName, String lvlPrefix){
        String className;
        if (oneType instanceof PsiPrimitiveType){
            className = ((PsiPrimitiveType) oneType).getName();
        }else {
            className = Objects.requireNonNull(PsiTypesUtil.getPsiClass(oneType)).getName();
        }
        if (StringUtils.equalsIgnoreCase(className, "int")
                || StringUtils.equalsIgnoreCase(className, "integer")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + RandomUtils.nextInt(0,Integer.MAX_VALUE) + ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "String")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + "\""+ getRandomString(RandomUtils.nextInt(10,40))+"\";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "boolean")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + (RandomUtils.nextInt(0, 1) % 2 == 0) +";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "date")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + "new Date();\n");
        } else if (StringUtils.equalsIgnoreCase(className, "long")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" +RandomUtils.nextLong(0,Long.MAX_VALUE)+ "L;\n");
        } else if (StringUtils.equalsIgnoreCase(className, "byte")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + RandomUtils.nextInt(0,Byte.MAX_VALUE)+ ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "float")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" +RandomUtils.nextFloat(0,Integer.MAX_VALUE)+ ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "double")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" +RandomUtils.nextDouble(0,Integer.MAX_VALUE)+ ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "bigDecimal")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + BigDecimal.valueOf(RandomUtils.nextDouble(0,Integer.MAX_VALUE)).setScale(2, RoundingMode.HALF_UP) + ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "char")||StringUtils.equalsIgnoreCase(className, "character")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + ((char)RandomUtils.nextInt(0,Character.MAX_VALUE)) + ";\n");
        } else if (StringUtils.equalsIgnoreCase(className, "datetime")){
            return new MockItem(oneVarName, className +" "+oneVarName + "=" + "LocalDateTime.now();\n");
        } else {
            return mockOneClass(Objects.requireNonNull(PsiTypesUtil.getPsiClass(oneType)),lvlPrefix);
        }
    }

    public static String getRandomString(int count) {
        return RandomStringUtils.random(count,"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_#$@!%");
    }

    private MockItem mockList(PsiType listType, String listVarName, String lvlPrefix){
        StringBuilder sb = new StringBuilder();
        PsiType[] parameters = null;
        if (listType instanceof PsiClassReferenceType){
            parameters = ((PsiClassReferenceType) listType).getParameters();
        }
        if (ArrayUtils.isEmpty(parameters)){
            sb.append("\n");
            sb.append("List ").append(listVarName).append("=").append("new ArrayList();");
            sb.append("\n");
        }else{
            PsiType parameter = parameters[0];
            PsiClass typeParameter = PsiTypesUtil.getPsiClass(parameter);
            if (typeParameter!=null){
                sb.append("\n");
                sb.append("List<").append(typeParameter.getName()).append("> ")
                        .append(listVarName).append("=").append("new ArrayList<>();");
                sb.append("\n");
                for (int i = 0; i <3; i++) {
                    String varInList;
                    MockItem mockItem;
                    if (StringUtils.equalsIgnoreCase(typeParameter.getName(), "list")){
                        varInList = PsiToolUtils.lowerStart(typeParameter.getName() + "s" + lvlPrefix + i);
                        mockItem = mockList(parameter, varInList, lvlPrefix+i);
                    }else{
                        varInList = PsiToolUtils.lowerStart(typeParameter.getName() + lvlPrefix + i);
                        mockItem = mockOne(parameter,varInList, lvlPrefix+i);
                    }
                    mockItem.setBlock(mockItem.getBlock().replace(mockItem.getVarName(), varInList));
                    sb.append(mockItem.getBlock());
                    sb.append(listVarName).append(".add(").append(varInList).append(");");
                    sb.append("\n");
                }
            }
        }
        return new MockItem(listVarName, sb.toString());
    }

    private String generateMethodWithParam(String caller,String methodName, String parameter){
        return caller + "." + methodName + "(" + parameter + ");\n";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        if (element.getPrevSibling() != null && element.getPrevSibling().getFirstChild() != null && element.getPrevSibling().getFirstChild().getFirstChild() != null && element.getPrevSibling().getFirstChild().getFirstChild() instanceof PsiTypeElement && ((PsiTypeElement) element.getPrevSibling().getFirstChild().getFirstChild()).getType() instanceof PsiClassType){
            return true;
        }
        return false;
    }

    private class MockItem{
        private String varName;
        private String block;

        public MockItem(String varName, String block) {
            this.varName = varName;
            this.block = block;
        }

        public String getVarName() {
            return varName;
        }

        public void setVarName(String varName) {
            this.varName = varName;
        }

        public String getBlock() {
            return block;
        }

        public void setBlock(String block) {
            this.block = block;
        }
    }
}
