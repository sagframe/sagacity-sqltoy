package org.sagacity.sqltoy.dao;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SqlToyDao 接口签名完整性检查工具
 */
public class SqlToyDaoChecker {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("SqlToyDao 接口签名完整性检查");
        System.out.println("========================================\n");
        
        try {
            // 加载 SqlToyDao 接口
            Class<?> sqlToyDaoInterface = Class.forName("org.sagacity.sqltoy.dao.SqlToyDao");
            Method[] interfaceMethods = sqlToyDaoInterface.getDeclaredMethods();
            
            System.out.println("SqlToyDao 接口总方法数：" + interfaceMethods.length + "\n");
            
            // 按前缀分组统计
            Map<String, List<Method>> methodGroups = new HashMap<>();
            for (Method method : interfaceMethods) {
                String prefix = getMethodPrefix(method.getName());
                methodGroups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(method);
            }
            
            System.out.println("方法分组统计:");
            System.out.println("----------------------------------------");
            methodGroups.entrySet().stream()
                .sorted(Map.Entry.<String, List<Method>>comparingByKey().reversed())
                .forEach(entry -> {
                    System.out.printf("%-20s: %d%n", entry.getKey(), entry.getValue().size());
                });
            System.out.println();
            
            // 检查 DefaultSqlToyDaoImpl
            checkImplementation(
                "DefaultSqlToyDaoImpl",
                "org.sagacity.sqltoy.dao.impl.DefaultSqlToyDaoImpl",
                interfaceMethods
            );
            
            // 检查 Spring 模块的 SqlToyDaoImpl
            checkImplementation(
                "SqlToyDaoImpl(Spring)",
                "org.sagacity.sqltoy.dao.impl.SqlToyDaoImpl",
                interfaceMethods
            );
            
            // 检查 Spring Starter 模块的 SqlToyDaoImpl
            checkImplementation(
                "SqlToyDaoImpl(Spring Starter)",
                "org.sagacity.sqltoy.dao.impl.SqlToyDaoImpl",
                interfaceMethods
            );
            
            // 检查 Solon 模块的 SqlToyDaoImpl
            checkImplementation(
                "SqlToyDaoImpl(Solon)",
                "org.sagacity.sqltoy.solon.dao.impl.SqlToyDaoImpl",
                interfaceMethods
            );
            
            System.out.println("\n========================================");
            System.out.println("检查完成！");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("检查过程出错：" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查实现类是否实现了所有接口方法
     */
    private static void checkImplementation(String implName, String implClassName, Method[] interfaceMethods) {
        System.out.println("\n----------------------------------------");
        System.out.println("检查：" + implName);
        System.out.println("----------------------------------------");
        
        try {
            Class<?> implClass = Class.forName(implClassName);
            Method[] implMethods = implClass.getDeclaredMethods();
            
            // 创建实现类方法的签名集合
            Set<String> implMethodSignatures = new HashSet<>();
            for (Method method : implMethods) {
                implMethodSignatures.add(getMethodSignature(method));
            }
            
            // 检查每个接口方法是否都有实现
            List<String> missingMethods = new ArrayList<>();
            for (Method interfaceMethod : interfaceMethods) {
                String signature = getMethodSignature(interfaceMethod);
                if (!implMethodSignatures.contains(signature)) {
                    missingMethods.add(interfaceMethod.getName() + 
                        "(" + Arrays.toString(interfaceMethod.getParameterTypes()) + ")");
                }
            }
            
            if (missingMethods.isEmpty()) {
                System.out.println("✓ 所有方法都已实现 (" + implMethods.length + " 个方法)");
            } else {
                System.out.println("✗ 缺少 " + missingMethods.size() + " 个方法:");
                missingMethods.forEach(m -> System.out.println("  - " + m));
            }
            
        } catch (ClassNotFoundException e) {
            System.out.println("⚠ 类未找到：" + implClassName);
            System.out.println("  (可能该模块还未编译或不存在)");
        } catch (Exception e) {
            System.out.println("✗ 检查失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取方法的唯一签名
     */
    private static String getMethodSignature(Method method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.getReturnType().getName())
                .append("#")
                .append(method.getName())
                .append("(");
        
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                signature.append(",");
            }
            signature.append(paramTypes[i].getName());
        }
        signature.append(")");
        
        return signature.toString();
    }
    
    /**
     * 获取方法名称的前缀
     */
    private static String getMethodPrefix(String methodName) {
        if (methodName.startsWith("findOne")) return "findOne*";
        if (methodName.startsWith("findAll")) return "findAll*";
        if (methodName.startsWith("findList")) return "findList*";
        if (methodName.startsWith("findTop")) return "findTop*";
        if (methodName.startsWith("findRandom")) return "findRandom*";
        if (methodName.startsWith("findPage")) return "findPage*";
        if (methodName.startsWith("findStream")) return "findStream*";
        if (methodName.startsWith("find")) return "find*";
        if (methodName.startsWith("save")) return "save*";
        if (methodName.startsWith("update")) return "update*";
        if (methodName.startsWith("delete")) return "delete*";
        if (methodName.startsWith("get")) return "get*";
        if (methodName.startsWith("set")) return "set*";
        if (methodName.startsWith("execute")) return "execute*";
        if (methodName.startsWith("batch")) return "batch*";
        if (methodName.startsWith("convert")) return "convert*";
        if (methodName.startsWith("translate")) return "translate*";
        if (methodName.startsWith("cache")) return "cache*";
        if (methodName.startsWith("generate")) return "generate*";
        if (methodName.equals("count")) return "count";
        if (methodName.equals("elastic")) return "elastic";
        if (methodName.equals("mongo")) return "mongo";
        if (methodName.equals("store")) return "store";
        if (methodName.equals("query")) return "query";
        if (methodName.equals("load")) return "load";
        if (methodName.equals("unique")) return "unique";
        if (methodName.equals("treeTable")) return "treeTable";
        if (methodName.equals("batch")) return "batch";
        if (methodName.equals("tableApi")) return "tableApi";
        if (methodName.equals("flush")) return "flush";
        if (methodName.equals("isUnique")) return "isUnique";
        if (methodName.equals("existCache")) return "existCache";
        if (methodName.equals("wrapTreeTableRoute")) return "wrapTreeTableRoute";
        return "other";
    }
}
