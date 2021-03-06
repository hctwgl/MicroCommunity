package com.java110.web.core;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.Arguments;
import org.thymeleaf.dom.Document;
import org.thymeleaf.dom.Element;
import org.thymeleaf.dom.Macro;
import org.thymeleaf.dom.Node;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.processor.element.AbstractMarkupSubstitutionElementProcessor;
import org.thymeleaf.util.DOMUtils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 组件 自定义标签功能类
 * Created by wuxw on 2019/3/18.
 */
public class VueComponentElement extends AbstractMarkupSubstitutionElementProcessor {

    private static Logger logger = LoggerFactory.getLogger(VueComponentElement.class);

    private static final String DIV_PROPERTY_COMPONENT = "data-component";

    private static final int DEFAULT_PRECEDENCE = 1000;


    protected VueComponentElement(String elementName) {
        super(elementName);
    }

    @Override
    protected List<Node> getMarkupSubstitutes(Arguments arguments, Element element) {

        //logger.debug("arg:{},element:{}", JSONObject.toJSONString(arguments),element.getAttributeValue("name"));
        List<Node> nodes = new ArrayList<>();
        //获取模板名称
        String componentName = element.getAttributeValue("name");

        logger.debug("正在解析组件{},{}", componentName, new Date().getTime());
        String html = VueComponentTemplate.findTemplateByComponentCode(componentName + "." + VueComponentTemplate.COMPONENT_HTML);
        if (html == null) {
            throw new RuntimeException("在缓存中未找到组件【" + componentName + "】");
        }
        //List<Node> tmpNodes = DOMUtils.getHtml5DOMFor(new StringReader(html)).getChildren();
        Document tmpDoc = DOMUtils.getLegacyHTML5DOMFor(new StringReader(html));
        List<Node> tmpNodes = tmpDoc.getChildren();

        addDataComponent(tmpDoc, componentName);
        for (Node tmpNode : tmpNodes) {
            nodes.add(tmpNode);
        }
        //css
        String css = VueComponentTemplate.findTemplateByComponentCode(componentName + "." + VueComponentTemplate.COMPONENT_CSS);
        if (css != null) {
            css = "<style type=\"text/css\">" + css + "</style>";
            Node nodeCss = new Macro(css);
            nodes.add(nodeCss);
        }

        //js
        String js = VueComponentTemplate.findTemplateByComponentCode(componentName + "." + VueComponentTemplate.COMPONENT_JS);
        if (js != null) {

            js = dealJs(js, element);
            js = dealJsAddComponentCode(js, element);
            js = "<script type=\"text/javascript\">//<![CDATA[ \n" + js + "//]]>\n</script>";
            Node nodeJs = new Macro(js);
            nodes.add(nodeJs);
        }

        logger.debug("解析完成组件{},{}", componentName, new Date().getTime());

        return nodes;
    }

    /**
     * 处理js
     *
     * @param element 页面元素
     * @param js      js文件内容
     * @return js 文件内容
     */
    private String dealJs(String js, Element element) {

        //在js 中检测propTypes 属性
        if (!js.contains("propTypes")) {
            return js;
        }

        //解析propTypes信息
        String tmpProTypes = js.substring(js.indexOf("propTypes"));
        tmpProTypes = tmpProTypes.substring(tmpProTypes.indexOf("{") + 1, tmpProTypes.indexOf("}")).trim();

        if (StringUtils.isEmpty(tmpProTypes)) {
            return js;
        }

        String[] tmpType = tmpProTypes.split(",");
        StringBuffer propsJs = new StringBuffer("\nvar $props = {};\n");
        for (String type : tmpType) {
            if (StringUtils.isEmpty(type) || !type.contains(":")) {
                continue;
            }
            String[] types = type.split(":");
            String attrKey = types[0].replace(" ", "")
                    .replace("\n", "")
                    .replace("\r", "");
            if (!element.hasAttribute(attrKey)) {
                String componentName = element.getAttributeValue("name");
                logger.error("组件" + componentName + "未配置组件属性 " + attrKey);
                throw new TemplateProcessingException("组件[" + componentName + "]未配置组件属性" + attrKey);
            }
            String vcType = element.getAttributeValue(attrKey);
            if (types[1].equals("vc.propTypes.string")) {
                vcType = "'" + vcType + "'";
            }
            propsJs.append("$props." + attrKey + "=" + vcType + ";\n");
        }

        //将propsJs 插入到 第一个 { 之后
        int position = js.indexOf("{");
        if (position < 0) {
            String componentName = element.getAttributeValue("name");
            logger.error("组件" + componentName + "对应js 未包含 {}  ");
            throw new TemplateProcessingException("组件" + componentName + "对应js 未包含 {}  ");
        }
        js = new StringBuffer(js).insert(position + 1, propsJs).toString();
        return js;
    }

    /**
     * 处理js 变量和 方法都加入 组件编码
     *
     * @param element 页面元素
     * @param js      js文件内容
     * @return js 文件内容
     */
    private String dealJsAddComponentCode(String js, Element element) {

        if (!element.hasAttribute("code")) {
            return js;
        }

        String code = element.getAttributeValue("code");

        return js.replace("@vc_", code);
    }

    /**
     * 加入组件名称到 HTML中 方便定位问题
     *
     * @param tmpDoc        页面节点
     * @param componentCode 组件编码
     */
    private void addDataComponent(Document tmpDoc, String componentCode) {
        Element tmpElement = tmpDoc.getFirstElementChild();
        tmpElement.setAttribute(DIV_PROPERTY_COMPONENT, componentCode);
    }

    @Override
    public int getPrecedence() {
        return DEFAULT_PRECEDENCE;
    }
}
