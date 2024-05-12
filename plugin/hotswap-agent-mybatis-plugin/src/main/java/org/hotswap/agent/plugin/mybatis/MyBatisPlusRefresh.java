package org.hotswap.agent.plugin.mybatis;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MyBatisPlusRefresh {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(MyBatisPlusRefresh.class);

    public static void refreshMapper(Configuration configuration, Class<?> mapperClass) {
        if (!(configuration instanceof MybatisConfiguration)) {
            return;
        }
        LOGGER.info("刷新MyBatis Plus Mapper:{}", mapperClass.getName());
        MybatisConfiguration plugConfiguration = (MybatisConfiguration) configuration;
        plugConfiguration.addNewMapper(mapperClass);
    }

    public static void refreshModel(Class<?> clazz) {
        if (clazz == null || clazz.isInterface()) {
            return;
        }
        if (!isEntity(clazz)) {
            return;
        }
        try {
            //参考 AbstractSqlInjector.inspectInject 方法
            Class<?> sqlSessionFactoryClz = Class.forName("org.apache.ibatis.session.defaults.DefaultSqlSessionFactory", true, AllExtensionsManager.getInstance().getClassLoader());
            Field staticConfiguration = null;
            try {
                staticConfiguration = sqlSessionFactoryClz.getDeclaredField("_staticConfiguration");
            } catch (NoSuchFieldException ex) {
                return;
            }
            @SuppressWarnings("unchecked")
            Configuration configuration = getConfiguration((ArrayList<Configuration>) staticConfiguration.get(null));
            if (configuration instanceof MybatisConfiguration) {
                MybatisConfiguration mybatisConfiguration = (MybatisConfiguration) configuration;
                Class<?> mapperClass = getMapperClass(mybatisConfiguration, clazz);
                if (mapperClass == null) {
                    return;
                }

                //移除自定义方法缓存
                Field mappedStatementsField = MybatisConfiguration.class.getDeclaredField("mappedStatements");
                mappedStatementsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, MappedStatement> mappedStatementsAll = (Map<String, MappedStatement>) mappedStatementsField.get(mybatisConfiguration);
                final String typeKey = mapperClass.getName() + StringPool.DOT;
                Set<String> mapperSet = mappedStatementsAll.keySet().stream().filter(ms -> ms.startsWith(typeKey)).collect(Collectors.toSet());
                if (!mapperSet.isEmpty()) {
                    List<String> methodNames = Arrays.stream("updateById,insert,selectById".split(",")).collect(Collectors.toList());
                    for (String key : mapperSet) {
                        String[] keys = key.split("\\.");
                        String methodName = keys[keys.length - 1];
                        if (methodNames.contains(methodName)) {
                            mappedStatementsAll.remove(key);
                        }
                    }
                }

                //构建MapperBuilderAssistant
                String xmlResource = mapperClass.getName().replace(StringPool.DOT, StringPool.SLASH) + ".java (best guess)";
                MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(configuration, xmlResource);
                builderAssistant.setCurrentNamespace(mapperClass.getName());

                //移除实体类对应的字段缓存，否则在初始化TableInfo的时候，不重新初始化字段集合
                Field field = ReflectionKit.class.getDeclaredField("CLASS_FIELD_CACHE");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Class<?>, List<Field>> cache = (Map<Class<?>, List<Field>>) field.get(null);
                cache.remove(clazz);

                //移除mapper缓存，否则不执行循环注入自定义方法  if (!mapperRegistryCache.contains(className)) {
                Set<String> mapperRegistryCache = GlobalConfigUtils.getMapperRegistryCache(builderAssistant.getConfiguration());
                String className = mapperClass.toString();
                mapperRegistryCache.remove(className);

                //移除实体类对应的表缓存，否则不重新初始化TableInfo
                TableInfoHelper.remove(clazz);

                //移除实体类对应的映射器缓存
                DefaultReflectorFactory reflectorFactory = (DefaultReflectorFactory) mybatisConfiguration.getReflectorFactory();
                Field reflectorMapField = DefaultReflectorFactory.class.getDeclaredField("reflectorMap");
                reflectorMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                ConcurrentMap<Class<?>, Reflector> reflectorMap = (ConcurrentMap<Class<?>, Reflector>) reflectorMapField.get(reflectorFactory);
                reflectorMap.remove(clazz);

                //注入自定义方法
                GlobalConfigUtils.getSqlInjector(configuration).inspectInject(builderAssistant, mapperClass);

                LOGGER.info("清理 Mybatis Plus Model Field 缓存:{}", clazz.getName());
            }

        } catch (Exception e) {
            LOGGER.error("Refresh Mybatis Model Failure", e);
        }
    }

    //获取Mapper class
    private static Class<?> getMapperClass(MybatisConfiguration mybatisConfiguration, Class<?> clazz) {
        Class<?> mapperClass = null;
        MapperRegistry mapperRegistry = mybatisConfiguration.getMapperRegistry();
        Collection<Class<?>> mappers = mapperRegistry.getMappers();
        for (Class<?> mapper : mappers) {
            Type[] superClassTypeArray = mapper.getGenericInterfaces();
            for (Type superClassType : superClassTypeArray) {
                if (!(superClassType instanceof ParameterizedType)) {
                    continue;
                }
                ParameterizedType parameterizedType = (ParameterizedType) superClassType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    Type modelType = Arrays.stream(actualTypeArguments).filter(type -> type.getTypeName().equals(clazz.getTypeName())).findAny().orElse(null);
                    if (modelType != null) {
                        mapperClass = mapper;
                        break;
                    }
                }
            }
            if (mapperClass != null) {
                break;
            }
        }
        return mapperClass;
    }

    public static boolean isEntity(Class<?> clazz) {
        TableName tableName = clazz.getAnnotation(TableName.class);
        if (Objects.nonNull(tableName)) {
            return true;
        }

        // 检查类是否直接继承 Model
        if (Model.class.isAssignableFrom(clazz)) {
            return true;
        }

        // 检查类的父类是否继承 Model
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            if (Model.class.isAssignableFrom(superClass)) {
                return true;
            }
            superClass = superClass.getSuperclass();
        }
        // 默认不是实体类
        return false;
    }

    public static Configuration getConfiguration(ArrayList<Configuration> configurations) {
        Configuration configuration = configurations.stream().filter(c -> c.getClass() == MybatisConfiguration.class).findFirst().orElse(null);
        if (configuration != null) {
            return configuration;
        }
        return configurations.get(0);
    }

}
