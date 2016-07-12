package controller;

import function.HandleJBC;
import javassist.*;
import javassist.bytecode.*;
import model.JBCClass;
import model.JBCField;
import model.JBCMethod;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by LimSJ on 2016-07-08.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class FileUploadController {

    private static Logger log = Logger.getLogger(FileUploadController.class.getName());

    @RequestMapping(path="/", method= RequestMethod.POST)
    public ArrayList<JBCClass> fildUploaded(@RequestParam("uploadFile") MultipartFile[] files){
        ArrayList<JBCClass> classes = new ArrayList<>();
        BufferedInputStream bufferedInputStream = null;
        ClassFile classFile = null;

        if(!isOkExtension(files)){
            System.err.println("==================================");
            System.err.println("올바르지 않은 파일 확장자 포함");
            return null;
        }

        log.info("/////////////////////////////////////////////");
        for(MultipartFile file : files){
            try {
                bufferedInputStream = new BufferedInputStream(file.getInputStream());
            } catch (IOException e) {
                System.err.println("====================");
                System.err.println("올바르지 않은 파일");
                e.printStackTrace();
                return null;
            }

            String fileName = file.getOriginalFilename();
            String ext = null;
            String[] splitted = fileName.split("\\.");
            if(splitted.length > 1)
                ext = splitted[splitted.length - 1].toLowerCase();

            if(ext.equals("class")){
                try {
                    classFile = new ClassFile(new DataInputStream(bufferedInputStream));
                } catch (IOException e) {
                    System.err.println("==================");
                    System.err.println("classFile 에러");
                    e.printStackTrace();
                }

                JBCClass jbcClass = null;
                if(classFile != null){
                    jbcClass = setJBCClass(classFile);
                    classes.add(jbcClass);
                    log.info(jbcClass.getClassName());
                }
            } else if(ext.equals("jar")){
                ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream);
                JBCClass jbcClass = null;
                try {
                    for (ZipEntry entry = zipInputStream.getNextEntry(); entry != null; entry = zipInputStream.getNextEntry()) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".class")) {
                            String className = entry.getName().replace('/', '.');
                            System.out.println(className);
                            classFile = new ClassFile(new DataInputStream(zipInputStream));
                            jbcClass = setJBCClass(classFile);
                            classes.add(jbcClass);
                            log.info(jbcClass.getClassName());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("===================");
                    System.err.println("IOException");
                    e.printStackTrace();
                }
            }
        }

        return classes;
    }

    /**
     * 파일 확장자가 전부 맞는지 확인
     * @param files 전송받은 파일들
     * @return OK 여부
     */
    private boolean isOkExtension(MultipartFile[] files){
        for(MultipartFile file : files){
            String fileName = file.getOriginalFilename();
            String[] splittedFileNames = fileName.split("\\.");
            String ext = null;
            if(splittedFileNames.length > 1)
                ext = splittedFileNames[splittedFileNames.length - 1].toLowerCase();

            if(!ext.equals("class") && !ext.equals("jar"))
                return false;
        }

        return true;
    }

    /**
     * classFile을 이용하여 JBCClass를 셋팅
     * @param classFile 분석할 ClassFile
     * @return 셋팅된 JBCClass
     */
    private JBCClass setJBCClass(ClassFile classFile){
        ClassPool classPool = ClassPool.getDefault();

        CtClass ctClass = classPool.makeClass(classFile);
        List<MethodInfo> methods = classFile.getMethods();
        List<FieldInfo> fields = classFile.getFields();
        CtConstructor[] ctConstructors = ctClass.getDeclaredConstructors();
        CtMethod[] ctMethods = ctClass.getDeclaredMethods();

        JBCClass jbcClass = new JBCClass(classFile.getName(), ctClass.getPackageName());
        jbcClass.setLoaded(true);
        jbcClass.setSuperClassName(classFile.getSuperclass());
        Collections.addAll(jbcClass.getInterfaceNames(), classFile.getInterfaces());

        // TODO: 2016-07-11 CtField로 바꿔야함
        for(FieldInfo field : fields){
            JBCField jbcField = new JBCField(jbcClass.getClassName(), field.getName());

            String accessModifier = HandleJBC.getModifier(field.getAccessFlags());
            jbcField.setAccessModifier(accessModifier);
            String returnType = HandleJBC.getFieldReturnType(field.getDescriptor());
            jbcField.setReturnType(returnType);

            jbcClass.getJBCFields().add(jbcField);
        }

        // 생성자
        for(CtConstructor ctConstructor : ctConstructors){
            JBCMethod jbcMethod = new JBCMethod(jbcClass.getClassName(), ctConstructor.getName());
            String accessModifier = HandleJBC.getModifier(ctConstructor.getModifiers());
            jbcMethod.setAccessModifier(accessModifier);

            // parameters
            ArrayList<String> params = HandleJBC.getMethodParameters(ctConstructor.getSignature());
            if(params != null)
                jbcMethod.getParameters().addAll(params);

            ArrayList<String> calledMethods = HandleJBC.getCalledMethods(ctConstructor);
            if(calledMethods != null)
                jbcMethod.getCalledMethods().addAll(calledMethods);

            jbcClass.getJBCMethods().add(jbcMethod);
        }

        // 함수
        for(CtMethod ctMethod : ctMethods){
            JBCMethod jbcMethod = new JBCMethod(jbcClass.getClassName(), ctMethod.getName());
            String accessModifier = HandleJBC.getModifier(ctMethod.getModifiers());
            jbcMethod.setAccessModifier(accessModifier);

            // return type
            String returnType = HandleJBC.getMethodReturnType(ctMethod.getSignature());
            jbcMethod.setReturnType(returnType);

            // parameters
            ArrayList<String> params = HandleJBC.getMethodParameters(ctMethod.getSignature());
            if(params != null)
                jbcMethod.getParameters().addAll(params);

            ArrayList<String> calledMethods = HandleJBC.getCalledMethods(ctMethod);
            if(calledMethods != null)
                jbcMethod.getCalledMethods().addAll(calledMethods);

            jbcClass.getJBCMethods().add(jbcMethod);
        }

        return jbcClass;
    }
}
