package com.futao.springbootdemo.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.futao.springbootdemo.foundation.configuration.HttpMessageConverterConfiguration;
import com.futao.springbootdemo.model.entity.ApiControllerDescription;
import com.futao.springbootdemo.model.entity.ApiMethodDescription;
import com.futao.springbootdemo.model.system.ErrorMessage;
import com.futao.springbootdemo.model.system.ErrorMessageFields;
import com.futao.springbootdemo.service.StatisticService;
import com.futao.springbootdemo.utils.SpringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author futao
 * Created on 2018-12-18.
 */
@Service
public class StatisticServiceImpl implements StatisticService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticServiceImpl.class);

    /**
     * 获取程序中定义的错误码
     *
     * @return
     * @throws IllegalAccessException
     */
    @Override
    @Cacheable("errorMessageList")
    public List<ErrorMessageFields> getErrorMessages() throws IllegalAccessException {
        Field[] fields = ErrorMessage.class.getFields();
        List<ErrorMessageFields> errorMessageFieldsList = new ArrayList<>(fields.length);
        for (Field it : fields) {
            errorMessageFieldsList.add(new ErrorMessageFields(it.getName(), it.get(ErrorMessage.class).toString()));
        }
        return errorMessageFieldsList;
    }

    /**
     * 获取程序中的枚举值
     *
     * @return
     */
    @Override
    @Cacheable("enumList")
    public Map<String, JSONArray> enumList() {
        Reflections reflections = new Reflections("com.futao.springbootdemo.model.enums");
        Set<Class<? extends Enum>> classSet = reflections.getSubTypesOf(Enum.class);
        Map<String, JSONArray> map = new HashMap<>(classSet.size());
        classSet.forEach(enumClass -> {
            JSONArray jsonArray = new JSONArray();
            map.put(enumClass.getSimpleName(), jsonArray);
            Arrays.stream(enumClass.getEnumConstants()).forEach(enumField -> {
                JSONObject kv = new JSONObject();
                jsonArray.add(new JSONObject().fluentPut(enumField.name(), kv));
                Arrays.stream(enumField.getClass().getDeclaredFields())
                        .filter(allField -> (!allField.isEnumConstant()) && !"$VALUES".equals(allField.getName()))
                        .forEach(field -> {
                            try {
                                field.setAccessible(true);
                                kv.fluentPut(field.getName(), field.get(enumField));

                            } catch (IllegalAccessException e1) {
                                e1.printStackTrace();
                            }
                        });
            });

        });
        return map;
    }


//    @Cacheable("apiList")

    /**
     * @return
     */
    @Override
    public ArrayList<ApiControllerDescription> apiList() {
//        Entry entry = null;
//        try {
//            entry = SphU.entry("apiList");
        return a();
//        } catch (BlockException e1) {
//            throw LogicException.ae(ErrorMessage.VISIT_TOO_FREQUENTLY);
//        } finally {
//            if (entry != null) {
//                entry.exit();
//            }
//        }
    }

    private ArrayList<ApiControllerDescription> a() {
        //获取所以标记有RestController注解的类
        Set<Class<?>> controllerClassSet = new Reflections("com.futao.springbootdemo.controller").getTypesAnnotatedWith(RestController.class);
        //容器
        ArrayList<ApiControllerDescription> apiDescriptions = new ArrayList<>(controllerClassSet.size());
        //遍历controller
        controllerClassSet.forEach(controllerClass -> {
            //methods容器
            List<ApiMethodDescription> apiMethodDescriptions = new ArrayList<>();
            //controller描述
            String apiDesc = controllerClass.getAnnotation(Api.class) == null ? "" : controllerClass.getAnnotation(Api.class).value();
            //获取controller路径(path或value)
            RequestMapping annotation = controllerClass.getAnnotation(RequestMapping.class);
            List<String> baseUrl = new ArrayList<>();
            if (annotation != null) {
                baseUrl = annotation.path().length == 0 ? Arrays.asList(annotation.value()) : Arrays.asList(annotation.path());
            }
            apiDescriptions.add(new ApiControllerDescription(apiDesc, baseUrl, controllerClass.getSimpleName(), apiMethodDescriptions));
            //controller下的method
            Arrays.stream(controllerClass.getMethods())
                    .forEach(method -> Arrays.stream(method.getAnnotations()).forEach(it -> {
                        Class<? extends Annotation> annotationType = it.annotationType();
                        if (annotationType == RequestMapping.class) {
                            RequestMapping requestMapping = (RequestMapping) it;
                            apiMethodDescriptions.add(new ApiMethodDescription(method.getName(),
                                    method.getAnnotation(ApiOperation.class) == null ? "" : method.getAnnotation(ApiOperation.class).value(),
                                    requestMapping.path().length == 0 ? requestMapping.value() : requestMapping.path()
                            ));
                        } else if (annotationType == GetMapping.class) {
                            GetMapping getMapping = (GetMapping) it;
                            apiMethodDescriptions.add(new ApiMethodDescription(method.getName(),
                                    method.getAnnotation(ApiOperation.class) == null ? "" : method.getAnnotation(ApiOperation.class).value(),
                                    getMapping.path().length == 0 ? getMapping.value() : getMapping.path()
                            ));
                        } else if (annotationType == PostMapping.class) {
                            PostMapping postMapping = (PostMapping) it;
                            apiMethodDescriptions.add(new ApiMethodDescription(method.getName(),
                                    method.getAnnotation(ApiOperation.class) == null ? "" : method.getAnnotation(ApiOperation.class).value(),
                                    postMapping.path().length == 0 ? postMapping.value() : postMapping.path()
                            ));
                        } else if (annotationType == PutMapping.class) {
                            PutMapping putMapping = (PutMapping) it;
                            apiMethodDescriptions.add(new ApiMethodDescription(method.getName(),
                                    method.getAnnotation(ApiOperation.class) == null ? "" : method.getAnnotation(ApiOperation.class).value(),
                                    putMapping.path().length == 0 ? putMapping.value() : putMapping.path()
                            ));
                        } else if (annotationType == DeleteMapping.class) {
                            DeleteMapping deleteMapping = (DeleteMapping) it;
                            apiMethodDescriptions.add(new ApiMethodDescription(method.getName(),
                                    method.getAnnotation(ApiOperation.class) == null ? "" : method.getAnnotation(ApiOperation.class).value(),
                                    deleteMapping.path().length == 0 ? deleteMapping.value() : deleteMapping.path()
                            ));
                        }
                    }));
        });
        return apiDescriptions;
    }


    /**
     * apiList版本2
     */
    public void apiListV2() {
        //Controller列表
        Map<String, Object> beansWithAnnotation = SpringUtils.getContext().getBeansWithAnnotation(Controller.class);
        //容器
        ArrayList<ApiControllerDescription> apiDescriptions = new ArrayList<>(beansWithAnnotation.size());
        beansWithAnnotation.forEach((k, v) -> {
            Class<?> aClass = v.getClass();
            Api apiAnn = aClass.getAnnotation(Api.class);
            Controller controllerAnn = aClass.getAnnotation(Controller.class);
            RequestMapping annotation = aClass.getAnnotation(RequestMapping.class);
        });
    }

    public static void main(String[] args) {
        StatisticServiceImpl statisticService = new StatisticServiceImpl();
        ArrayList<ApiControllerDescription> a = statisticService.a();
//        System.out.println(JSON.toJSONString(a, HttpMessageConverterConfiguration.SERIALIZER_FEATURES));
        Set<String> list = new HashSet<>();
        a.forEach(controller -> {
            //controllerPath
            List<String> baseUrl = controller.getBaseUrl();
            //方法
            List<ApiMethodDescription> methodDescriptionList = controller.getMethodDescriptionList();
            methodDescriptionList.forEach(method -> {
                String[] methodUrls = method.getMethodUrl();
                if (baseUrl.size() == 0) {
                    for (String methodUrl : methodUrls) {
                        list.add("/" + methodUrl);
                    }
                } else {
                    baseUrl.forEach(bUrl -> {
                        for (String methodUrl : methodUrls) {
                            list.add("/" + bUrl + "/" + methodUrl);
                        }
                    });
                }
            });
        });
        System.out.println(JSON.toJSONString(list, HttpMessageConverterConfiguration.SERIALIZER_FEATURES));
    }
}
