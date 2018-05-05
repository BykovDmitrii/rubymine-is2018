package com.jetbrains.python.inspection;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Vector;
public class PyConstantExpression extends PyInspection {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
                                          @NotNull LocalInspectionToolSession session) {
        return new Visitor(holder, session);
    }

    private static class Visitor extends PyInspectionVisitor {
        class PolizStackElem{
            private long value;
            private char type;

            public PolizStackElem(String val){
                if(isBool(val)) {
                    type = 'b';
                    if (val.compareTo("true") == 0)
                        value = 1;
                    else
                        value = 0;
                }
                else
                    if(isNumber(val)){
                        type = 'i';
                        value = Long.valueOf(val);
                    }
            }

            public long getValue() {
                return value;
            }

            public void setValue(long value) {
                this.value = value;
            }

            public char getType() {
                return type;
            }

            public void setType(char type) {
                this.type = type;
            }
        }
        private Vector<String> operations;
        private Vector<String> stackOperations;
        private Vector<String> polizExpression;
        private Vector<PolizStackElem> polizStack;

        private Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
            super(holder, session);
            operations = new Vector<String>(0);
            operations.addElement(")");
            operations.addElement("or");
            operations.addElement("and");
            operations.addElement("not");
            operations.addElement("==");
            operations.addElement("!=");
            operations.addElement(">");
            operations.addElement("<");
            operations.addElement("+");
            operations.addElement("-");
            operations.addElement("%");
            operations.addElement("*");
            operations.addElement("/");
            operations.addElement("//");
            operations.addElement("**");
            operations.addElement("+u");
            operations.addElement("-u");
            operations.addElement("(");
            clear();
        }

        @Override
        public void visitPyIfStatement(PyIfStatement node) {
            super.visitPyIfStatement(node);
            processIfPart(node.getIfPart());
            for (PyIfPart part : node.getElifParts()) {
                processIfPart(part);
            }
        }

        private void clear(){
            polizStack = new Vector<PolizStackElem>(0);
            stackOperations = new Vector<String>(0);
            stackOperations.addElement("(");
        }

        private boolean isNumber(String str) {
            for (int i = 0; i < str.length(); i++)
                if ((str.charAt(i) < '0') || (str.charAt(i) > '9' ))
                    if((i==0)&&(str.length()>1)){
                        if((str.charAt(i)!='-') && (str.charAt(i)!='+'))
                            return false;
                    }
                    else
                        return false;
            return true;
        }

        private boolean isBool(String str){
            switch (str.toLowerCase()){
                case "true" :
                    return true;
                case "false" :
                    return true;
            }
            return false;
        }

        private boolean isLogOperator(String str){
            switch (str.toLowerCase()){
                case "and" :
                    return true;
                case "or" :
                    return true;
                case "not" :
                    return true;
            }
            return false;
        }

        private boolean isPlusOrMinus(String str){
            switch (str.toLowerCase()){
                case "+" :
                    return true;
                case "-" :
                    return true;
            }
            return false;
        }

        private boolean isMultiply(String str){
            switch (str.toLowerCase()){
                case "*" :
                    return true;
                case "**" :
                    return true;
                case "/" :
                    return true;
                case "//" :
                    return true;
                case "%" :
                    return true;
            }
            return false;
        }

        private boolean isAriOperator(String str){
            return (isPlusOrMinus(str)||isMultiply(str)||(str.compareTo("**")==0));
        }

        private boolean isCompOperator(String str){
            switch (str.toLowerCase()){
                case "==" :
                    return true;
                case "!=" :
                    return true;
                case "<" :
                    return true;
                case ">" :
                    return true;
            }
            return false;
        }

        private boolean isUnary(String str){
            if(str.compareTo("+u") * str.compareTo("-u") == 0)
                return true;
            return false;
        }

        private boolean isBrackets(String str){
            switch (str.toLowerCase()) {
                case ")":
                    return true;
                case "(":
                    return true;
            }
            return false;
        }

        private boolean isNormalString(String str) {
            if (isNumber(str))
                return true;
            if(isLogOperator(str))
                return true;
            if(isAriOperator(str))
                return true;
            if(isCompOperator(str))
                return true;
            if(isBrackets(str))
                return true;
            if(isBool(str))
                return true;
            return false;
        }

        private String addSpaces(String expr){
            String newStr = "";
            boolean wasSign = false;
            int i = 0;
            while( i < expr.length()){
                char c = expr.charAt(i);
                i++;
                if((c == '(')||(c==')')||(c=='+')||(c=='-')||(c=='%')||(c=='<')||(c=='>')){
                    newStr += ' ';
                    newStr += c;
                    newStr += ' ';
                }
                else
                    if ((c=='!')||(c=='=')||(c=='*')||(c=='/')){
                        newStr += ' ';
                        newStr += c;
                        c = expr.charAt(i);
                        if((c=='=')||(c=='*')||(c=='/')) {
                            i++;
                            newStr += c;
                        }
                        newStr += ' ';
                    }
                    else
                        newStr += c;
            }
            return newStr;
        }

        private boolean isConstantIntExpression(String expr) {
            expr = addSpaces(expr);
            String str[] = expr.split(" ");
            for (String i : str) {
                 if(!isNormalString(i))
                     return false;
            }
            return true;
        }

        private void throwStack(){
            while ((stackOperations.lastElement().compareTo("(") != 0)){
                polizExpression.addElement(stackOperations.lastElement());
                stackOperations.removeElementAt(stackOperations.size()-1);
            }

            stackOperations.removeElementAt(stackOperations.size()-1);
        }

        private void throwStack(int in){
            while ((in <= priority(stackOperations.lastElement()))&&(stackOperations.lastElement().compareTo("(") != 0)){
                polizExpression.addElement(stackOperations.lastElement());
                stackOperations.removeElementAt(stackOperations.size()-1);
            }

        }

        private int priority(String operation){
            if(isAriOperator(operation)){
                if(isPlusOrMinus(operation))
                    return operations.indexOf("+");
                if(isMultiply(operation))
                    return operations.indexOf("*");
                return operations.indexOf(operation);
            }
            else{
                return operations.indexOf(operation);
            }
        }

        private void getPoliz(String expression[]){
            polizExpression = new Vector<String>(0);
            int ii=0;
            for(String i:expression) {
                if ((i.compareTo("") != 0)&&(i.compareTo(" ") != 0)) {
                    if (isNumber(i))
                        polizExpression.addElement(i);
                    else
                        if(isBool(i))
                            polizExpression.addElement(i.toLowerCase());
                    else {
                        if (((stackOperations.lastElement().compareTo("(")!=0))&&((ii = priority(i)) <= priority(stackOperations.lastElement()))){
                            if (i.compareTo(")") != 0) {
                                throwStack(ii);
                                stackOperations.addElement(i);
                            }
                            else {
                                throwStack();
                            }
                        }
                        else {
                            if (i.compareTo(")") == 0)
                                throwStack();
                            else
                                stackOperations.addElement(i);
                        }
                    }
                }
            }
            throwStack();
        }

        private boolean isConst(String s){
            return isBool(s)||isNumber(s);
        }
        
        private void runOperation(String oper){
            if(isAriOperator(oper)){
                runAriOp(oper);
            }
            else{
                if(isLogOperator(oper)){
                    runLogOp(oper);
                }
                else{
                    if(isCompOperator(oper)){
                        runCompOp(oper);
                    }
                    else
                        if(isUnary(oper)){
                            runUnaryOper(oper);
                        }
                }
            }
        }

        private void runUnaryOper(String oper){
            PolizStackElem a = polizStack.lastElement();
            polizStack.removeElementAt(polizStack.size() - 1);
            if(oper.compareTo("+u") == 0)
                polizStack.addElement(new PolizStackElem(Long.toString(a.getValue())));
            else
                polizStack.addElement(new PolizStackElem(Long.toString(-1 * a.getValue())));
        }

        private void runCompOp(String oper) {
            PolizStackElem a = polizStack.lastElement();
            polizStack.removeElementAt(polizStack.size() - 1);
            PolizStackElem b = polizStack.lastElement();
            polizStack.removeElementAt(polizStack.size() - 1);
            switch (oper){
                case "==":
                    polizStack.addElement(new PolizStackElem(a.getValue() == b.getValue() ? "true":"false"));
                    return;
                case "!=":
                    polizStack.addElement(new PolizStackElem(a.getValue() != b.getValue()?"true":"false"));
                    return;
                case ">":
                    polizStack.addElement(new PolizStackElem(b.getValue() > a.getValue()? "true" :"false"));
                    return;
                case "<":
                    polizStack.addElement(new PolizStackElem(b.getValue() < a.getValue()? "true" : "false"));
                    return;
            }
        }

        private void runLogOp(String oper) {
            if(oper.compareTo("not") == 0){
                PolizStackElem a = polizStack.lastElement();
                polizStack.removeElementAt(polizStack.size() - 1);
                if(a.getValue() != 0)
                    polizStack.addElement(new PolizStackElem("0"));
                else
                    polizStack.addElement(new PolizStackElem("1"));
            }
            else{
                if(oper.compareTo("and") == 0){
                    boolean b = true;
                    PolizStackElem a = polizStack.lastElement();
                    polizStack.removeElementAt(polizStack.size() - 1);
                    b &= (a.getValue() != 0);
                    a = polizStack.lastElement();
                    polizStack.removeElementAt(polizStack.size() - 1);
                    b &= (a.getValue() != 0);
                    if(b)
                        polizStack.addElement(new PolizStackElem("1"));
                    else
                        polizStack.addElement(new PolizStackElem("0"));
                }
                else{
                    boolean b = false;
                    PolizStackElem a = polizStack.lastElement();
                    polizStack.removeElementAt(polizStack.size() - 1);
                    b |= (a.getValue() != 0);
                    a = polizStack.lastElement();
                    polizStack.removeElementAt(polizStack.size() - 1);
                    b|=(a.getValue() != 0);
                    if(b)
                        polizStack.addElement(new PolizStackElem("1"));
                    else
                        polizStack.addElement(new PolizStackElem("0"));
                }
            }
        }

        private void runAriOp(String oper) {
            PolizStackElem b = polizStack.lastElement();
            polizStack.removeElementAt(polizStack.size() - 1);
            PolizStackElem a = polizStack.lastElement();
            polizStack.removeElementAt(polizStack.size() - 1);
            switch (oper){
                case "+":
                    polizStack.addElement(new PolizStackElem(Long.toString(a.getValue() + b.getValue())));
                    return;
                case "-":
                    polizStack.addElement(new PolizStackElem(Long.toString(a.getValue() - b.getValue())));
                    return;
                case "*":
                    polizStack.addElement(new PolizStackElem(Long.toString(a.getValue() * b.getValue())));
                    return;
                case "/":
                    polizStack.addElement(new PolizStackElem(Long.toString(div(a.getValue() , b.getValue()))));
                    return;
                case "%":
                    polizStack.addElement(new PolizStackElem(Long.toString(mod(a.getValue() , b.getValue()))));
                    return;
                case "//":
                    polizStack.addElement(new PolizStackElem(Long.toString(div(a.getValue() , b.getValue()))));
                    return;
                case "**":
                    polizStack.addElement(new PolizStackElem(Long.toString(pow(a.getValue(),b.getValue()))));
                    return;
            }
        }

        private long div(long a, long b){
            if(b != 0)
                return a / b;
            else
                throw new IllegalArgumentException("");
        }

        private long mod(long a, long b){
            if(b != 0)
                return a % b;
            else
                throw new IllegalArgumentException("");

        }

        private long pow(long value, long value1) {
            int res=1;
            while(value1>0){
                res*=value;
                value1--;
            }
            return res;
        }

        private boolean calculatePoliz(){
            for(String it : polizExpression){
                if(isConst(it)){
                    polizStack.addElement(new PolizStackElem(it));
                }
                else{
                    runOperation(it);
                }
            }
            if(polizStack.size() != 1)
                throw new IllegalArgumentException("");
            return polizStack.elementAt(0).getValue()!=0;
        }
        
        private boolean calculate(String expr){
            expr = addSpaces(expr);
            String expression[] = expr.split(" ");
            expression = defUnary(expression);
            getPoliz(expression);
            return calculatePoliz();
        }

        private String[] defUnary(String[] expression) {
            boolean wasNum = false;
            Vector<String> newExpr = new Vector<String>();
            for(String s : expression){
                if ((s.compareTo("") != 0)&&(s.compareTo(" ") != 0)) {
                    if (isPlusOrMinus(s)) {
                        if (!wasNum) {
                            if (s.compareTo("+") == 0)
                                newExpr.addElement("+u");
                            else {
                                newExpr.addElement("-u");
                            }
                        }
                        else {
                            newExpr.addElement(s);
                            wasNum = false;
                        }
                    }
                    else {
                        newExpr.addElement(s);
                        wasNum = isNumber(s) || s.compareTo(")") == 0;
                    }
                }
                else
                    newExpr.addElement(s);
            }
            newExpr.copyInto(expression);
            return expression;
        }

        private void processIfPart(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();
            if (condition instanceof PyBoolLiteralExpression) {
                registerProblem(condition, "The condition is always " + ((PyBoolLiteralExpression) condition).getValue());
            }
            else {
                String Expr = condition.getText();
                if (isConstantIntExpression(Expr)) {
                    try
                    {
                        boolean b = calculate(Expr);
                        registerProblem(condition, "The condition is always " + b);
                        clear();
                    }
                    catch (IllegalArgumentException e){
                        clear();
                    }
                }
            }
        }
    }
}