FROM python:3.9

COPY . /workdir/
WORKDIR /workdir/



RUN pip install --user firebase-admin==4.5.2
RUN pip install --user schedule==1.2.2
RUN pip install --user finnhub-python==2.4.23

#RUN pip install --user websocket-client==1.8.0
#RUN pip install --user polygon-api-client==1.14.5


CMD ["python3","-u","main.py"]
