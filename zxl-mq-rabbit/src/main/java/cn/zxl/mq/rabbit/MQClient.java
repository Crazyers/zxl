package cn.zxl.mq.rabbit;

import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.InitializingBean;

import cn.zxl.common.LogUtil;

/**
 * ͨ��MQ�ͻ���
 * 
 * @author xiaolongzuo
 *
 */
public class MQClient extends AbstractMqBean implements InitializingBean {

	public static Logger logger = Logger.getLogger(MQClient.class);

	public static final String DEFAULT_EXCHANGE = "";

	/** ������ָ���ͻ��˷��͵Ľ�������Ĭ��Ϊdirect���͵Ŀս���������ѡ�� */
	private String exchange = DEFAULT_EXCHANGE;

	/** ������ָ���ͻ��˷��͵�·�ɼ�����exchange��ֵ����fanout���͵Ľ�����ʱ��������Ϊ��ѡ����ѡ�� */
	private String routingKey;

	public void setRoutingKey(String routingKey) {
		this.routingKey = routingKey;
	}

	public void setExchange(String exchange) {
		this.exchange = exchange;
	}

	public void send(Object message) {
		if (message == null) {
			throw new NullPointerException("���ܷ��Ϳ���Ϣ��");
		}
		Message sendMessage = null;
		try {
			sendMessage = mqMessageConverter.toSendMessage(message);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		int index = chooseIndex(sendMessage);
		try {
			mqTemplates.get(index).send(exchange, routingKey, sendMessage);
		} catch (Exception exception) {
			LogUtil.warn(logger, "������Ϣʧ�ܣ������ط���", exception);
			resend(index, sendMessage, true);
		}
	}

	public <T> T sendAndReceive(Object message, Class<T> receiveMessageClass) {
		if (message == null) {
			throw new NullPointerException("���ܷ��Ϳ���Ϣ��");
		}
		if (receiveMessageClass == null) {
			throw new NullPointerException("���ܵ���Ϣ���Ͳ���Ϊ�գ�");
		}
		Message sendMessage = null;
		try {
			sendMessage = mqMessageConverter.toSendMessage(message);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		Message messageBean = null;
		int index = chooseIndex(sendMessage);
		try {
			messageBean = mqTemplates.get(index).sendAndReceive(exchange, routingKey, sendMessage);
		} catch (Exception exception) {
			LogUtil.warn(logger, "������Ϣʧ�ܣ������ط���", exception);
			messageBean = resend(index, sendMessage, false);
		}
		T result = null;
		if (messageBean != null) {
			try {
				result = mqMessageConverter.fromMessage(messageBean, receiveMessageClass);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("���ܵķ��ؽ��Ϊ�գ�");
		}
		return result;
	}

	private Message resend(int excludeIndex, Message sendMessage, boolean isAsyn) {
		Message messageBean = null;
		for (int i = 0; i < size && i != excludeIndex; i++) {
			try {
				if (isAsyn) {
					mqTemplates.get(i).send(exchange, routingKey, sendMessage);
				} else {
					messageBean = mqTemplates.get(i).sendAndReceive(exchange, routingKey, sendMessage);
				}
				LogUtil.info(logger, "�ѳɹ��ط���Ϣ��");
				break;
			} catch (Exception e) {
				LogUtil.warn(logger, "�����ط�ʧ�ܣ�", e);
				continue;
			}
		}
		return messageBean;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		List<RabbitAdmin> rabbitAdmins = getMqAdmins();
		for (int i = 0; i < rabbitAdmins.size(); i++) {
			RabbitAdmin rabbitAdmin = rabbitAdmins.get(i);
			while (true) {
				if (!DEFAULT_EXCHANGE.equals(exchange)) {
					break;
				}
				try {
					rabbitAdmin.declareQueue(new Queue(routingKey, queueDurable, queueExclusive, queueAutoDelete, queueArguments));
					rabbitAdmin.declareBinding(new Binding(routingKey, DestinationType.QUEUE, MQClient.DEFAULT_EXCHANGE, routingKey, bindingArguments));
					break;
				} catch (Exception e) {
					LogUtil.warn(logger, "�������У�" + routingKey + "��ʧ��", e);
				}
			}
		}
	}

}
