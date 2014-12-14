package cn.zxl.mvc.common.action;

public enum ErrorMessage {

	NO_RESULT {
		@Override
		public String getErrorMessage() {
			return "û�з��ؽ����";
		}
	},
	DEFAULT {
		@Override
		public String getErrorMessage() {
			return "ϵͳ�����ڲ�����";
		}
	};

	public abstract String getErrorMessage();

}
